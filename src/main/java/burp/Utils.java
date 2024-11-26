package burp;
import burp.models.DataEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static JFrame Dialog_Edit;

    public static String JsontoStr(List<DataEntry> all_data) {
        // 使用 Jackson 库将 DataEntry 对象序列化为 JSON 字符串
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(all_data);
        } catch (JsonProcessingException ex) {
            BurpExtender.printException(ex, "Error converting DataEntry to JSON");
            return "";
        }
    }

    public static ArrayList<DataEntry> StrtoJson(String all_data) {
        // 使用 Jackson 库将 JSON 字符串转换为 ArrayList<DataEntry> 对象
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // 指定具体的类型
            return objectMapper.readValue(all_data, objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, DataEntry.class));
        } catch (JsonProcessingException ex) {
            System.out.println("Error converting JSON to DataEntry: " + ex.getMessage());
            return null;
        }
    }

    public static void CopytoClipboard(String text) {


        // 获取系统剪切板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection stringSelection = new StringSelection(text);
        // 将文本复制到剪切板
        clipboard.setContents(stringSelection, null);
        // 提示用户复制成功
    }

    public static String readFile(String filePath) {

        StringBuilder content = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line); // 直接拼接每行内容，不添加换行符
            }
        } catch (IOException e) {
            return "";
        }
        return content.toString();
    }

    public static void edit(DataEntry data) {
        Dialog_Edit = new JFrame("编辑");
        JPanel jpanel_edit = new JPanel();

        GridBagLayout gbl = new GridBagLayout();//创建网格包布局管理器
        GridBagConstraints gbc = new GridBagConstraints();//GridBagConstraints对象来给出每个组件的大小和摆放位置
        jpanel_edit.setLayout(gbl);//设置容器布局为网格包布局类型
        gbc.fill = GridBagConstraints.BOTH;//组件填充显示区域，当格子有剩余空间时，填充空间
        gbc.insets = new Insets(5, 5, 5, 5); // 边距
        gbc.anchor = GridBagConstraints.NORTHWEST; // 对齐方式
        //        gridx 和 gridy：指定组件放置在网格中的起始位置。
        //        gridwidth 和 gridheight：指定组件跨越的网格单元数量。
        //        fill：指定组件如何填充其网格单元。
        //        weightx 和 weighty：指定组件在可伸缩空间中的权重
        JLabel enable = new JLabel("Enable:");
        JCheckBox enable_text = new JCheckBox("Enable Rule");
        enable_text.setSelected(data.enable);

        JLabel item = new JLabel("Item:");
        JComboBox<String> item_text = new JComboBox<>(new String[]{"Request header", "Request body","Response header","Response body"});
        item_text.setSelectedItem(data.item);

        JLabel match = new JLabel("Match:");
        JTextField match_text = new JTextField(data.match);

        JLabel replace = new JLabel("Replace:");
        if(data.type.contains("Set Var")){
            replace.setText("VarName");
        }
        JTextArea replace_text = new JTextArea(data.replace);
        replace_text.setLineWrap(true); // 启用自动换行
        replace_text.setWrapStyleWord(true); // 在单词边界处换行
//        replace_text.setAutoscrolls(true);
//        replace_text.setBorder( BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        JScrollPane replace_scroll = new JScrollPane(replace_text);


        JLabel comment = new JLabel("Comment:");
        JTextField comment_text = new JTextField(data.comment);

        JLabel type = new JLabel("Type:");
        JComboBox<String> type_combox = new JComboBox<>(new String[]{"Replace", "Regex","Set Var","Regex Set Var"});
        type_combox.setSelectedItem(data.type);
        type_combox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 获取选中的项
                String selectedValue = (String) type_combox.getSelectedItem();
//                BurpExtender.printSuccessMessage(selectedValue);
                if(Objects.equals(selectedValue, "Set Var") || Objects.equals(selectedValue, "Regex Set Var")){
                    replace.setText("VarName");
                }else {
                    replace.setText("Replace:");
                }
            }
        });

        jpanel_edit.add(Add_Component(gbl, enable, gbc, 0, 0, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, enable_text, gbc, 1, 0, 1, 1, 1, 0));
        jpanel_edit.add(Add_Component(gbl, type, gbc, 0, 1, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, type_combox, gbc, 1, 1, 1, 1, 1, 0));
        jpanel_edit.add(Add_Component(gbl, item, gbc, 0, 2, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, item_text, gbc, 1, 2, 1, 1, 1, 0));
        jpanel_edit.add(Add_Component(gbl, match, gbc, 0, 3, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, match_text, gbc, 1, 3, 1, 1, 1, 1));
        jpanel_edit.add(Add_Component(gbl, replace, gbc, 0, 4, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, replace_scroll, gbc, 1, 4, 1, 1, 1, 0));
        jpanel_edit.add(Add_Component(gbl, comment, gbc, 0, 5, 1, 1, 0, 0));
        jpanel_edit.add(Add_Component(gbl, comment_text, gbc, 1, 5, 1, 1, 1, 0));

        JButton save = new JButton("Save");
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                data.item = (String) item_text.getSelectedItem();
                data.match = match_text.getText();
                data.replace = replace_text.getText();
                if(data.match.isEmpty()){
                    JOptionPane.showMessageDialog(null, "Match cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if(data.replace.isEmpty()){
                    JOptionPane.showMessageDialog(null, "Replace cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                data.comment = comment_text.getText();
                data.type = (String) type_combox.getSelectedItem();
                data.enable = enable_text.isSelected();
                BurpExtender.table_model.setValueBySelectRow(data);
                Dialog_Edit.dispose();
                BurpExtender.printSuccessMessage("Successfully saved!");
                BurpExtender.savePersistentSettings();

            }
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> Dialog_Edit.dispose());
        JPanel button_jpanel =new JPanel();
        button_jpanel.setLayout(new BoxLayout(button_jpanel, BoxLayout.X_AXIS));
        button_jpanel.add(cancel);
        button_jpanel.add(Box.createHorizontalStrut(10)); // 10像素的间距
        button_jpanel.add(save);

        gbc.fill = GridBagConstraints.NORTH;//组件填充显示区域，当格子有剩余空间时，填充空间
        gbc.anchor = GridBagConstraints.CENTER; // 对齐方式

        jpanel_edit.add(Add_Component(gbl, button_jpanel, gbc, 0, 6, 1, 2, 0, 0));
        jpanel_edit.setBorder(new EmptyBorder(10, 10, 10, 10)); // 设置外边距为10
        Dialog_Edit.getContentPane().add(jpanel_edit);
        Dialog_Edit.setSize(800, 350);
        Dialog_Edit.setLocationRelativeTo(null);
        Dialog_Edit.setVisible(true);
    }

    public static Component Add_Component(GridBagLayout gbl, Component comp, GridBagConstraints gbc, int gridx, int gridy, int gridheight, int gridwidth, int weight_x, int weight_y) {
        gbc.weightx = weight_x;
        gbc.weighty = weight_y;
        gbc.gridheight = gridheight;
        gbc.gridwidth = gridwidth;
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        gbl.setConstraints(comp, gbc);
        return comp;
    }
//    public static Map<String,Object> getRequestsResponseHeadBody(byte[] req_resp_byte) {
//        Map<String,Object> return_sult = new HashMap<>();
//        String headersPart = new String(req_resp_byte, StandardCharsets.UTF_8);
//        String[] http_body_head = headersPart.split("\n\n|\r\n\r\n");
//        return_sult.put("header",http_body_head[0]);
//        if(http_body_head.length>=2){
//            return_sult.put("body",http_body_head[1]);
//        }else{
//            return_sult.put("body","");
//        }
//        return return_sult;
//    }

    public static String handleData(DataEntry data, String source_text) {
        if (Objects.equals(data.type, "Regex")) {
            return handleRegexData(data, source_text);
        } else if (Objects.equals(data.type, "Set Var") || Objects.equals(data.type, "Regex Set Var")) {
            return  handleSetVar(data,source_text);
        } else {
            return handleNonRegexData(data, source_text);
        }

    }

    public static String handleRegexData(DataEntry data, String sourceText) {
        try {
            // 编译正则表达式
            Pattern pattern = Pattern.compile(data.match);
            Matcher matcher = pattern.matcher(sourceText);
            // 查找并替换所有匹配项
            return  matcher.replaceAll(data.replace);
        } catch (Exception e) {
            BurpExtender.printException(e, "Error processing regex");
            return sourceText; // 返回原始文本，防止因错误导致数据丢失
        }
    }

    public static String handleNonRegexData(DataEntry data, String sourceText) {
        // 使用 String.replace 方法替换所有匹配的部分
        return sourceText.replace(data.match, data.replace);
    }
    public static String handleSetVar(DataEntry data, String sourceText) {
        Boolean  is_set_vat = true;
        try {
            String value="";
            if(Objects.equals(data.type, "Regex Set Var")){
                try {
                    // 尝试编译正则表达式
                    Pattern pattern = Pattern.compile(data.match);
                    Matcher matcher = pattern.matcher(sourceText);
                    // 查找第一个匹配项
                    if (matcher.find()) {
                        value = matcher.group();
                    }
                } catch (Exception e) {
                    is_set_vat=false;
                    value = "";

                }
            }else{
                value = data.match;
            }
            if(is_set_vat&&!BurpExtender.lock_var){
                if(!Objects.equals(BurpExtender.Global_Var.get(data.replace), value) && BurpExtender.serverStarted){
                    BurpExtender.setVar( data.replace,(String)value);
                }else{
                    BurpExtender.printDebugMessage("Service not down not set var:" + data.replace+"="+value);
                }
            }else{
                BurpExtender.printDebugMessage("Variable is lock not set var:" + data.replace+"="+value);
            }

        } catch (Exception e) {
            BurpExtender.printException(e, "Error processing regex");
            return sourceText; // 返回原始文本，防止因错误导致数据丢失
        }
        return  sourceText;

    }
//    public static String encodeHtml(String input) {
//        return input.replace("&", "&amp;")
//                .replace("<", "&lt;")
//                .replace(">", "&gt;")
//                .replace("\"", "&quot;")
//                .replace("'", "&#39;");
//    }
}