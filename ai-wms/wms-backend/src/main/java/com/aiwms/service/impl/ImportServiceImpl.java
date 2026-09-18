package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.ImportResultVO;
import com.aiwms.dto.excel.InventoryImportRow;
import com.aiwms.dto.excel.LocationImportRow;
import com.aiwms.dto.excel.ProductImportRow;
import com.aiwms.entity.*;
import com.aiwms.mapper.*;
import com.alibaba.excel.EasyExcel;
import com.aiwms.service.ImportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据导入实现
 *
 * <p>设计原则：
 * ① **逐行校验，部分成功**——真实导入很少全成功，
 * 把失败行和原因返回给用户，让他修正后重传那几行
 * ② **幂等**——已存在的数据跳过而不是报错，方便重复导入
 * ③ **批量插入**——避免逐行 insert 导致性能问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final WarehouseAreaMapper areaMapper;
    private final LocationMapper locationMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;

    private static final Map<String, Integer> TYPE_MAP = Map.of(
            "存储区", 0, "拣货区", 1, "收货区", 2, "发货区", 3);

    // ==================================================================
    //  ① 商品导入
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importProducts(MultipartFile file) {
        List<ProductImportRow> rows = readExcel(file, ProductImportRow.class);

        ImportResultVO result = new ImportResultVO();
        result.setType("商品");
        result.setTotalRows(rows.size());

        // 已有款号缓存（避免每行都查库）
        Map<String, Long> existProducts = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getReference, Product::getId));
        Set<String> existSkuCodes = skuMapper.selectList(null).stream()
                .map(ProductSku::getSkuCode).collect(Collectors.toSet());

        // ---- 第一遍：校验 + 收集需要新建的「款」 ----
        // 中间结构：(款号, ABC分类, 尺码, SKU编码)
        record ValidRow(String ref, String abc, BigDecimal size, String skuCode) {}
        List<ValidRow> valid = new ArrayList<>();
        Set<String> newRefs = new LinkedHashSet<>();
        Map<String, String> refToAbc = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            ProductImportRow row = rows.get(i);
            int excelRow = i + 2;      // Excel 行号（含表头）

            // ---- 逐行校验 ----
            if (!StringUtils.hasText(row.getReference())) {
                result.addError(excelRow, "款号不能为空");
                continue;
            }
            if (!StringUtils.hasText(row.getSizeUs())) {
                result.addError(excelRow, "尺码不能为空");
                continue;
            }
            BigDecimal size;
            try {
                size = new BigDecimal(row.getSizeUs().trim());
            } catch (NumberFormatException e) {
                result.addError(excelRow, "尺码格式错误: " + row.getSizeUs());
                continue;
            }
            String abc = StringUtils.hasText(row.getAbcClass())
                    ? row.getAbcClass().trim().toUpperCase() : "C";
            if (!List.of("A", "B", "C").contains(abc)) {
                result.addError(excelRow, "ABC 分类只能是 A/B/C: " + row.getAbcClass());
                continue;
            }

            String ref = row.getReference().trim();
            String skuCode = StringUtils.hasText(row.getSkuCode())
                    ? row.getSkuCode().trim()
                    : ref + "-" + size.stripTrailingZeros().toPlainString();

            // ---- 幂等：已存在的 SKU 跳过 ----
            if (existSkuCodes.contains(skuCode)) {
                result.setSkippedRows(result.getSkippedRows() + 1);
                continue;
            }

            refToAbc.put(ref, abc);
            if (!existProducts.containsKey(ref)) {
                newRefs.add(ref);          // 款不存在，待新建
            }
            valid.add(new ValidRow(ref, abc, size, skuCode));
            existSkuCodes.add(skuCode);
        }

        // ---- 第二遍：批量新建「款」，建立完整 款号→id 映射 ----
        for (String ref : newRefs) {
            Product p = new Product();
            p.setReference(ref);
            p.setAbcClass(refToAbc.getOrDefault(ref, "C"));
            p.setSector("PF");
            productMapper.insert(p);
            existProducts.put(ref, p.getId());
        }

        // ---- 第三遍：批量新建 SKU（此时 productId 一定拿得到） ----
        for (ValidRow v : valid) {
            ProductSku sku = new ProductSku();
            sku.setProductId(existProducts.get(v.ref()));
            sku.setSizeUs(v.size());
            sku.setSkuCode(v.skuCode());
            skuMapper.insert(sku);
            result.setSuccessRows(result.getSuccessRows() + 1);
        }

        log.info("商品导入完成: 成功 {} 行, 跳过 {} 行, 失败 {} 行",
                result.getSuccessRows(), result.getSkippedRows(), result.getFailedRows());
        return result;
    }

    // ==================================================================
    //  ② 库位导入
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importLocations(MultipartFile file) {
        List<LocationImportRow> rows = readExcel(file, LocationImportRow.class);

        ImportResultVO result = new ImportResultVO();
        result.setType("库位");
        result.setTotalRows(rows.size());

        Map<String, Long> existAreas = areaMapper.selectList(null).stream()
                .collect(Collectors.toMap(WarehouseArea::getAreaCode, WarehouseArea::getId));
        Set<String> existCodes = locationMapper.selectList(null).stream()
                .map(Location::getLocationCode).collect(Collectors.toSet());

        List<Location> newLocs = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            LocationImportRow row = rows.get(i);
            int excelRow = i + 2;

            if (!StringUtils.hasText(row.getLocationCode())) {
                result.addError(excelRow, "库位号不能为空");
                continue;
            }
            String code = row.getLocationCode().trim();
            if (existCodes.contains(code)) {
                result.setSkippedRows(result.getSkippedRows() + 1);
                continue;
            }
            if (!StringUtils.hasText(row.getAreaCode())) {
                result.addError(excelRow, "库区不能为空");
                continue;
            }

            // 库区不存在则自动创建
            String areaCode = row.getAreaCode().trim();
            Long areaId = existAreas.get(areaCode);
            if (areaId == null) {
                WarehouseArea a = new WarehouseArea();
                a.setAreaCode(areaCode);
                a.setAreaName(areaCode + " 区");
                areaMapper.insert(a);
                existAreas.put(areaCode, a.getId());
                areaId = a.getId();
            }

            Integer type = TYPE_MAP.getOrDefault(
                    StringUtils.hasText(row.getLocationType()) ? row.getLocationType().trim() : "拣货区", 1);

            Location loc = new Location();
            loc.setLocationCode(code);
            loc.setAreaId(areaId);
            loc.setLocationType(type);
            loc.setXCoord(row.getXCoord() == null ? 0 : row.getXCoord());
            loc.setYCoord(row.getYCoord() == null ? 0 : row.getYCoord());
            loc.setZCoord(row.getZCoord() == null ? 1 : row.getZCoord());
            loc.setCapacity(row.getCapacity() == null ? 18 : row.getCapacity());
            loc.setUsedSlots(0);
            loc.setStatus(0);
            newLocs.add(loc);
            existCodes.add(code);
            result.setSuccessRows(result.getSuccessRows() + 1);
        }

        for (Location l : newLocs) {
            locationMapper.insert(l);
        }

        log.info("库位导入完成: 成功 {} 行, 跳过 {} 行, 失败 {} 行",
                result.getSuccessRows(), result.getSkippedRows(), result.getFailedRows());
        return result;
    }

    // ==================================================================
    //  ③ 期初库存导入
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importInventory(MultipartFile file) {
        List<InventoryImportRow> rows = readExcel(file, InventoryImportRow.class);

        ImportResultVO result = new ImportResultVO();
        result.setType("期初库存");
        result.setTotalRows(rows.size());

        Map<String, Long> skuMap = skuMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductSku::getSkuCode, ProductSku::getId));
        Map<String, Long> locMap = locationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Location::getLocationCode, Location::getId));
        Set<String> existPairs = inventoryMapper.selectList(null).stream()
                .map(i -> i.getSkuId() + "_" + i.getLocationId())
                .collect(Collectors.toSet());

        for (int i = 0; i < rows.size(); i++) {
            InventoryImportRow row = rows.get(i);
            int excelRow = i + 2;

            if (!StringUtils.hasText(row.getSkuCode())) {
                result.addError(excelRow, "SKU 编码不能为空");
                continue;
            }
            if (!StringUtils.hasText(row.getLocationCode())) {
                result.addError(excelRow, "库位号不能为空");
                continue;
            }
            if (row.getQty() == null || row.getQty() < 0) {
                result.addError(excelRow, "数量必须是非负整数");
                continue;
            }

            Long skuId = skuMap.get(row.getSkuCode().trim());
            Long locId = locMap.get(row.getLocationCode().trim());
            if (skuId == null) {
                result.addError(excelRow, "SKU 不存在: " + row.getSkuCode() + "（请先导入商品）");
                continue;
            }
            if (locId == null) {
                result.addError(excelRow, "库位不存在: " + row.getLocationCode() + "（请先导入库位）");
                continue;
            }

            String pairKey = skuId + "_" + locId;
            if (existPairs.contains(pairKey)) {
                result.setSkippedRows(result.getSkippedRows() + 1);
                continue;
            }

            // 写库存
            Inventory inv = new Inventory();
            inv.setSkuId(skuId);
            inv.setLocationId(locId);
            inv.setQty(row.getQty());
            inv.setQtyAllocated(0);
            inv.setQtyPicked(0);
            inv.setQtyOnhold(0);
            inv.setQtyAvailable(row.getQty());
            inv.setVersion(0);
            inventoryMapper.insert(inv);
            existPairs.add(pairKey);

            // ★ 同步写一条 RECEIPT 流水——保证库存能和流水对上账
            if (row.getQty() > 0) {
                InventoryTransaction tx = new InventoryTransaction();
                tx.setSkuId(skuId);
                tx.setLocationId(locId);
                tx.setQtyDelta(row.getQty());
                tx.setBizType("RECEIPT");
                tx.setReferenceType("INIT");
                tx.setReferenceId(0L);
                tx.setRemark("期初库存导入");
                tx.setCreatedBy("import");
                transactionMapper.insert(tx);
            }

            result.setSuccessRows(result.getSuccessRows() + 1);
        }

        log.info("期初库存导入完成: 成功 {} 行, 跳过 {} 行, 失败 {} 行",
                result.getSuccessRows(), result.getSkippedRows(), result.getFailedRows());
        return result;
    }

    // ==================================================================
    //  模板下载
    // ==================================================================

    @Override
    public void writeTemplate(String type, OutputStream out) {
        switch (type) {
            case "product" -> {
                ProductImportRow demo = new ProductImportRow();
                demo.setReference("8N10W9");
                demo.setAbcClass("A");
                demo.setSizeUs("41");
                demo.setSkuCode("8N10W9-41");
                EasyExcel.write(out, ProductImportRow.class)
                        .sheet("商品导入模板").doWrite(List.of(demo));
            }
            case "location" -> {
                LocationImportRow demo = new LocationImportRow();
                demo.setLocationCode("A-14-11");
                demo.setAreaCode("A");
                demo.setLocationType("拣货区");
                demo.setXCoord(368);
                demo.setYCoord(0);
                demo.setZCoord(1);
                demo.setCapacity(18);
                EasyExcel.write(out, LocationImportRow.class)
                        .sheet("库位导入模板").doWrite(List.of(demo));
            }
            case "inventory" -> {
                InventoryImportRow demo = new InventoryImportRow();
                demo.setSkuCode("8N10W9-41");
                demo.setLocationCode("A-14-11");
                demo.setQty(100);
                EasyExcel.write(out, InventoryImportRow.class)
                        .sheet("期初库存导入模板").doWrite(List.of(demo));
            }
            default -> throw new BusinessException("未知模板类型: " + type);
        }
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("product", "location", "inventory");
    }

    // ==================================================================
    //  内部
    // ==================================================================

    private <T> List<T> readExcel(MultipartFile file, Class<T> clazz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !(name.endsWith(".xlsx") || name.endsWith(".xls"))) {
            throw new BusinessException("只支持 .xlsx / .xls 格式");
        }
        try {
            return EasyExcel.read(file.getInputStream()).head(clazz).sheet().doReadSync();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
    }
}
