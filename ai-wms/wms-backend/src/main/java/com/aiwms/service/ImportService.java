package com.aiwms.service;

import com.aiwms.dto.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;

/**
 * 数据导入服务
 *
 * <p>对应真实 WMS 的「数据初始化」环节：
 * 系统部署后是空的，业务数据由用户通过 Excel 导入。
 */
public interface ImportService {

    /** 导入商品主数据（款 + 尺码 → SKU） */
    ImportResultVO importProducts(MultipartFile file);

    /** 导入库位主数据 */
    ImportResultVO importLocations(MultipartFile file);

    /** 导入期初库存 */
    ImportResultVO importInventory(MultipartFile file);

    /** 生成导入模板（供下载） */
    void writeTemplate(String type, OutputStream out);

    /** 支持的导入类型 */
    List<String> supportedTypes();
}
