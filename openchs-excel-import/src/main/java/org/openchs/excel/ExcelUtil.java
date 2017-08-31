package org.openchs.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.util.Date;

class ExcelUtil {
    static String getText(Row row, int cellNum) {
        Cell cell = row.getCell(cellNum);
        if (cell == null) return "";
        String stringCellValue = cell.toString();
        return stringCellValue.trim().replaceAll(" +", " ");
    }

    static Date getDate(Row row, int cellNum) {
        Cell cell = null;
        try {
            cell = row.getCell(cellNum);
            if (cell == null) return null;
            return cell.getDateCellValue();
        } catch (RuntimeException e) {
            System.err.println(String.format("getDate failed for row_number=%d, cell_number=%d, it contains:%s", row.getRowNum(), cellNum, cell.toString()));
            throw e;
        }
    }

    static Double getNumber(Row row, int cellNum) {
        Cell cell = null;
        try {
            cell = row.getCell(cellNum);
            if (cell == null) return null;
            return cell.getNumericCellValue();
        } catch (RuntimeException e) {
            System.err.println(String.format("getNumber failed for row_number=%d, cell_number=%d, it contains:%s", row.getRowNum(), cellNum, cell.toString()));
            throw e;
        }
    }
}