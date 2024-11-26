package burp.models;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class CustTableModel extends AbstractTableModel {
    private final String[] columnNames ={"id","Item", "Match", "Replace/Var Name","Type","Comment","Enabled"};
    public static List<DataEntry> table_data = new ArrayList<>();//用于展现结果

    @Override
    public int getRowCount()
    {
        return table_data.size();

    }

    @Override
    public int getColumnCount()
    {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int columnIndex)
    {
        return columnNames[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        if (columnIndex == 6) {
            return Boolean.class;
        }
        else if (columnIndex == 0) {
            return Integer.class;
        }
        return String.class;
    }


    public  void ClearData() {
        table_data.clear();
        // 通知视图数据发生了变化
        fireTableDataChanged();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        if (rowIndex < 0 || rowIndex >= table_data.size() || columnIndex < 0 || columnIndex >= columnNames.length) {
            return null;
        }
        DataEntry DataEntry = table_data.get(rowIndex);
        switch (columnIndex)
        {
            case 0:
                return  DataEntry.id;
//                return BurpExtender.callbacks.getToolName(DataEntry.tool);
            case 1:
                return DataEntry.item;
            case 2:
                return DataEntry.match;//返回响应包的长度
            case 3:
                return DataEntry.replace;
            case 4:
                return DataEntry.type;
            case 5:
                return DataEntry.comment;
            case 6:
                return DataEntry.enable;
            default:
                return "";
        }
    }

    public DataEntry getRowValueAt(int rowIndex)
    {
        if (rowIndex < 0 || rowIndex >= table_data.size()) {
            return null;
        }
        return table_data.get(rowIndex);
    }

    public void delValueAt(int rowIndex)
    {
        if (rowIndex < 0 || rowIndex >= table_data.size()) {
            return;
        }
        table_data.remove(rowIndex);
        fireTableDataChanged();
    }
    public List<DataEntry> getAllValue(){
        return  table_data;
    }
//    public DataEntry getValueByid(int id){
//        for(DataEntry i :table_data){
//            if(i.id==id){
//                return i;
//            }
//        }
//        return  null;
//    }


    public void ImportData(List<DataEntry> allData,Boolean isClear) {
        if (isClear) {
            // 清空现有数据
            table_data.clear();
            // 添加新数据
            // 通知表格数据已更改
        } else {
            // 添加新数据
            for (DataEntry data : allData) {
                if(!table_data.isEmpty()){
                    data.id = table_data.get(table_data.size()-1).id + 1;
                }else{
                    data.id=1;
                }
            }
        }
        table_data.addAll(allData);
        fireTableDataChanged();
    }

    public void setValueBySelectRow(DataEntry data) {
        boolean is_add=false;
        for(DataEntry i :table_data){
            if(i.id==data.id){
                is_add=true;
                i.item=data.item;
                i.match=data.match;
                i.replace=data.replace;
                i.type=data.type;
                i.comment=data.comment;
                i.enable=data.enable;
                fireTableDataChanged();
                break;
            }
        }
        if(!is_add){
            if(!table_data.isEmpty()){
                data.id = table_data.get(table_data.size()-1).id + 1;
            }else{
                data.id=1;
            }
            table_data.add(data);
        }
        fireTableDataChanged();
    }


}