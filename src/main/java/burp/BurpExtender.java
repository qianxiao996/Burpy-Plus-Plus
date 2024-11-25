package burp;
import burp.models.CustTableModel;
import burp.models.DataEntry;
import burp.ui.MessageDialog;
import net.razorvine.pyro.PyroException;
import net.razorvine.pyro.PyroProxy;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static burp.Utils.*;


public class BurpExtender extends Component implements IBurpExtender, ITab, ActionListener, IContextMenuFactory, MouseListener, IExtensionStateListener, IIntruderPayloadProcessor,IHttpListener,IMessageEditorTabFactory {
	private final String ExtenderName = "Burpy++";
	private final String ExtenderVersion="v4.1";

    private static IBurpExtenderCallbacks callbacks;
	private IExtensionHelpers helpers;

	public static CustTableModel table_model = new CustTableModel();
	private static PrintWriter stdout;
	private PrintWriter stderr;

	private JPanel mainPanel;
	public PyroProxy pyroBurpyService;
	private Process pyroServerProcess;

	private static JTextField pythonPath;
	private String pythonScript;
	private static JTextField pyroHost;
	private static JTextField pyroPort;
	private JTextPane serverStatus;

	private static JTextField burpyPath;

	private static JCheckBox chckbxPro;
	private static JCheckBox chckbxAuto;

	private static JCheckBox proxy_chckbxAuto;

	public Boolean should_pro = false;
	public Boolean should_auto = false;
	public Boolean proxy_should_auto =false;

	private Style redStyle;
	private Style greenStyle;
	DefaultStyledDocument documentServerStatus;

	private boolean serverStarted=false;
	private IContextMenuInvocation currentInvocation;

	private JButton clearConsoleButton;
	private JButton reloadScript;

	private static JEditorPane pluginConsoleTextArea;

	private Thread stdoutThread;
	private Thread stderrThread;

	private Boolean thread_stout_flag;
	private Boolean thread_error_flag;

	private static boolean lastPrintIsJS=false;
	public List<String> burpyMethods;
	public String serviceHost;
	public int servicePort;
	public String serviceObj="BurpyServicePyro";

	private Boolean is_match_replace=false;
	private Boolean is_match_replace_repeater=false;
	private Boolean is_match_replace_proxy=false;
	private Boolean is_match_replace_other=false;
	private static Boolean is_debug=false;
	private List<String> whitelist=new ArrayList<>();

	private static JCheckBox checkbox_enable_match_repalce;
	private static JCheckBox checkbox_enable_match_repalce_proxy;
	private static JCheckBox checkbox_enable_match_repalce_repeater;
	private static JCheckBox checkbox_enable_match_repalce_EXTENDER;
	private static JTextField white_host;

	@Override
	public void registerExtenderCallbacks(IBurpExtenderCallbacks c) {

		// Keep a reference to our callbacks object
		callbacks = c;

		// Obtain an extension helpers object
		helpers = callbacks.getHelpers();

		// Set our extension name
		callbacks.setExtensionName(ExtenderName+" "+ExtenderVersion);

		// register ourselves as an Intruder payload processor
		callbacks.registerIntruderPayloadProcessor(this);


		//register to produce options for the context menu
		callbacks.registerContextMenuFactory(this);

		// register to execute actions on unload
		callbacks.registerExtensionStateListener(this);

		// register editor tab
		callbacks.registerMessageEditorTabFactory(this);

		// Initialize stdout and stderr
		stdout = new PrintWriter(callbacks.getStdout(), true);
		stderr = new PrintWriter(callbacks.getStderr(), true);
		stdout.println("Welcome to Burpy++");
		stdout.println("Github: https://github.com/qianxiao996/Burpy-Plus-Plus");
		stdout.println("");
		try {
			InputStream inputStream = getClass().getClassLoader().getResourceAsStream("res/burpyServicePyro.py");
            BufferedReader reader = null;
            if (inputStream != null) {
                reader = new BufferedReader(new InputStreamReader(inputStream ));
            }
            File outputFile = new File(System.getProperty("java.io.tmpdir") + FileSystems.getDefault().getSeparator() + "burpyServicePyro.py");

			FileWriter fr = new FileWriter(outputFile);
			BufferedWriter br  = new BufferedWriter(fr);

			String s;
            if (reader != null) {
                while ((s = reader.readLine())!=null) {

                    br.write(s);
                    br.newLine();

                }
            }
            if (reader != null) {
                reader.close();
            }
            br.close();

			pythonScript = outputFile.getAbsolutePath();

		} catch(Exception e) {
			printException(e,"Error copying Pyro Server file");
		}

		SwingUtilities.invokeLater(new Runnable()  {
			@Override
			public void run()  {

				JTabbedPane tabbedPanel = new JTabbedPane();
				JTable table = new JTable(table_model);
				table.setAutoCreateRowSorter(true);
				if(callbacks.loadExtensionSetting("matchReplaceTable") != null) {
					try {
						table_model.ImportData(StrtoJson(callbacks.loadExtensionSetting("matchReplaceTable")), true);
					} catch (Exception e) {
						printException(e, "Error loading Match and Replace Table Value:"+ callbacks.loadExtensionSetting("matchReplaceTable"));
					}
				}
				JScrollPane scrollPane = new JScrollPane(table);
				JButton match_add = new JButton("Add");
				match_add.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						DataEntry data = new DataEntry(-1,"", "", "", "", "",true);
						Utils.edit(data);
						savePersistentSettings();
					}
				});
				JButton match_edit = new JButton("Edit");
				match_edit.addActionListener(new ActionListener() {
					 @Override
					 public void actionPerformed(ActionEvent e) {

						 int selectedRow = table.getSelectedRow();
						 if (!(selectedRow >= 0 && selectedRow < table_model.getRowCount())) {
							 JOptionPane.showMessageDialog(BurpExtender.this, "No row selected", "Error", JOptionPane.ERROR_MESSAGE);
							 return;
						 }

						 DataEntry data = table_model.getRowValueAt(selectedRow);

						 Utils.edit(data);
						 savePersistentSettings();
					 }
				 });

				JButton match_remove = new JButton("Remove");
				match_remove.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						int selectedRow = table.getSelectedRow();
						if(!(selectedRow >= 0 && selectedRow < table_model.getRowCount())){
							JOptionPane.showMessageDialog(BurpExtender.this, "No row selected", "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						table_model.delValueAt(selectedRow);
						savePersistentSettings();
					}
				});
				JButton match_clear = new JButton("Clear");
				match_clear.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						table_model.ClearData();
					}
				});
				JButton match_copy = new JButton("Copy");
				match_copy.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						int selectedRow = table.getSelectedRow();
						if(!(selectedRow >= 0 && selectedRow < table_model.getRowCount())){
							JOptionPane.showMessageDialog(BurpExtender.this, "No row selected", "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						DataEntry data = table_model.getRowValueAt(selectedRow);
						List<DataEntry>  data_all = new ArrayList<>();
						data_all.add(data);
						String json_str = Utils.JsontoStr(data_all);
						CopytoClipboard(json_str);
//						JOptionPane.showMessageDialog(BurpExtender.this, "Text copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
					}
				});
				JButton match_paste = new JButton("Paste");
				match_paste.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						// 获取系统剪切板
						Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
						// 尝试从剪切板中获取文本
						Transferable contents = clipboard.getContents(null);
						try {
							if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
								String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
								List<DataEntry> all_data = StrtoJson(text);
								if(all_data==null || all_data.isEmpty()){
									JOptionPane.showMessageDialog(BurpExtender.this, "Invalid JSON data in clipboard", "Error", JOptionPane.ERROR_MESSAGE);
									return;
								}
								table_model.ImportData(all_data,false);
								savePersistentSettings();
							} else {
								JOptionPane.showMessageDialog(BurpExtender.this, "Clipboard does not contain text", "Error", JOptionPane.ERROR_MESSAGE);
							}
						} catch (UnsupportedFlavorException | IOException ex) {
							printException(ex, "Error pasting from clipboard");
							JOptionPane.showMessageDialog(BurpExtender.this, "Error reading from clipboard: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						}
					}
				});
				JButton match_import = new JButton("Import");
				match_import.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						JFileChooser chooser = new JFileChooser();
						// 创建一个文件过滤器，仅显示 .txt 文件
						FileNameExtensionFilter pyFileFilter = new FileNameExtensionFilter("Json files", "json");
						chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);//设置只能选择目录
						chooser.addChoosableFileFilter(pyFileFilter);
						int returnVal = chooser.showOpenDialog(BurpExtender.this);
						if(returnVal == JFileChooser.APPROVE_OPTION) {
							String selectPath =chooser.getSelectedFile().getPath() ;
							String text = Utils.readFile(selectPath);
							List<DataEntry> all_data = StrtoJson(text);
							if(all_data==null || all_data.isEmpty()){
								JOptionPane.showMessageDialog(BurpExtender.this, "Invalid JSON data in clipboard", "Error", JOptionPane.ERROR_MESSAGE);
								return;
							}
							table_model.ImportData(all_data,true);
							chooser.setVisible(false);
							savePersistentSettings();
						}
					}
				});
				JButton match_export = new JButton("Export");
				match_export.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						JFileChooser chooser = new JFileChooser();
                        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
                        // 创建一个文件过滤器，仅显示 .txt 文件
						FileNameExtensionFilter pyFileFilter = new FileNameExtensionFilter("Json files", "json");
						chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);//设置只能选择目录
						chooser.addChoosableFileFilter(pyFileFilter);

						// 显示保存文件对话框
						int result = chooser.showSaveDialog(BurpExtender.this);

						// 检查用户是否点击了“保存”按钮
						if (result == JFileChooser.APPROVE_OPTION) {
							// 获取用户选择的文件
							java.io.File file = chooser.getSelectedFile();
							String contentToSave = Utils.JsontoStr(table_model.getAllValue());

							// 将内容写入文件
							try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
								writer.write(contentToSave);
								CopytoClipboard(contentToSave);
								JOptionPane.showMessageDialog(BurpExtender.this, "File saved successfully: " + file.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
							} catch (IOException ex) {
								printException(ex, "Error writing to file");
							}
							chooser.setVisible(false);
						}
					}
				});

				JPanel matchPanel = new JPanel();
				GridBagLayout gbl=new GridBagLayout();//创建网格包布局管理器
				GridBagConstraints gbc_match=new GridBagConstraints();//GridBagConstraints对象来给出每个组件的大小和摆放位置
				matchPanel.setLayout(gbl);//设置容器布局为网格包布局类型
				gbc_match.fill=GridBagConstraints.BOTH;//组件填充显示区域，当格子有剩余空间时，填充空间
				gbc_match.insets = new Insets(5, 5, 5, 5); // 边距
				gbc_match.anchor = GridBagConstraints.NORTHWEST; // 对齐方式
				//        gridx 和 gridy：指定组件放置在网格中的起始位置。
				//        gridwidth 和 gridheight：指定组件跨越的网格单元数量。
				//        fill：指定组件如何填充其网格单元。
				//        weightx 和 weighty：指定组件在可伸缩空间中的权重

				JLabel title = new JLabel("Match and Replace Rules");
				checkbox_enable_match_repalce = new JCheckBox("Enable");
				checkbox_enable_match_repalce.setSelected(callbacks.loadExtensionSetting("match_replace_enable") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable"), "1"));
				is_match_replace = callbacks.loadExtensionSetting("match_replace_enable") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable"), "1");

				checkbox_enable_match_repalce.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						is_match_replace = checkbox_enable_match_repalce.isSelected();
						savePersistentSettings();
					}
				});

				checkbox_enable_match_repalce_proxy = new JCheckBox("Enable Proxy");
                checkbox_enable_match_repalce_proxy.setSelected(callbacks.loadExtensionSetting("match_replace_enable_proxy") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_proxy"), "1"));
				is_match_replace_proxy = callbacks.loadExtensionSetting("match_replace_enable_proxy") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_proxy"), "1");
				checkbox_enable_match_repalce_proxy.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						is_match_replace_proxy = checkbox_enable_match_repalce_proxy.isSelected();
						savePersistentSettings();
					}
				});

				checkbox_enable_match_repalce_repeater = new JCheckBox("Enable Repeater");
				checkbox_enable_match_repalce_repeater.setSelected(callbacks.loadExtensionSetting("match_replace_enable_repeater") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_repeater"), "1"));
				is_match_replace_repeater = callbacks.loadExtensionSetting("match_replace_enable_repeater") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_repeater"), "1");

				checkbox_enable_match_repalce_repeater.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						is_match_replace_repeater = checkbox_enable_match_repalce_repeater.isSelected();
						savePersistentSettings();
					}
				});
				checkbox_enable_match_repalce_EXTENDER = new JCheckBox("Enable Extender");
				checkbox_enable_match_repalce_EXTENDER.setSelected(callbacks.loadExtensionSetting("match_replace_enable_extender") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_extender"), "1"));
				is_match_replace_other = callbacks.loadExtensionSetting("match_replace_enable_extender") != null && Objects.equals(callbacks.loadExtensionSetting("match_replace_enable_extender"), "1");
				checkbox_enable_match_repalce_EXTENDER.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						is_match_replace_other = checkbox_enable_match_repalce_EXTENDER.isSelected();
						savePersistentSettings();
					}
				});

				JCheckBox jcheckbox_debug= new JCheckBox("Debug");
				jcheckbox_debug.setSelected(callbacks.loadExtensionSetting("is_debug") != null && Objects.equals(callbacks.loadExtensionSetting("is_debug"), "1"));
				is_debug = callbacks.loadExtensionSetting("is_debug") != null && Objects.equals(callbacks.loadExtensionSetting("is_debug"), "1");
				jcheckbox_debug.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						is_debug = jcheckbox_debug.isSelected();
						savePersistentSettings();
					}
				});

				JLabel white_label = new JLabel("White List:");
				white_host =  new JTextField();
				if(callbacks.loadExtensionSetting("whiteList") != null) {
					try {
						white_host.setText(callbacks.loadExtensionSetting("whiteList"));
						if(callbacks.loadExtensionSetting("whiteList").isEmpty()){
							whitelist =new ArrayList<>();
							whitelist.add("*");
						}else{
							whitelist = Arrays.asList(callbacks.loadExtensionSetting("whiteList").split(","));
						}
					} catch (Exception e) {
						printException(e, "Error parsing whiteList");
					}
				}else{
					whitelist =new ArrayList<>();
					whitelist.add("*");
				}
				// 添加焦点监听器
				white_host.addFocusListener(new FocusAdapter() {
					@Override
					public void focusLost(FocusEvent e) {
						String white_value = white_host.getText();
//						printSuccessMessage("输入完成: " + white_host.getText());
						if(white_value.isEmpty()|| white_value.equals("*")){
							whitelist = new ArrayList<>();
							whitelist.add("*");
						}else if (white_value.contains(",")){
							whitelist = Arrays.asList(white_value.split(","));
						}else{
							whitelist = new ArrayList<>();
							whitelist.add(white_value);
						}
						printDebugMessage("Set up a white list: " + whitelist);
					}
				});
				JPanel tools_enable= new JPanel();
				tools_enable.setLayout(new BoxLayout(tools_enable, BoxLayout.X_AXIS));
				tools_enable.add(checkbox_enable_match_repalce);
				tools_enable.add(Box.createHorizontalStrut(10)); // 10像素的间距
				tools_enable.add(checkbox_enable_match_repalce_proxy);
				tools_enable.add(Box.createHorizontalStrut(10)); // 10像素的间距
				tools_enable.add(checkbox_enable_match_repalce_repeater);
				tools_enable.add(Box.createHorizontalStrut(10)); // 10像素的间距
				tools_enable.add(checkbox_enable_match_repalce_EXTENDER);
				tools_enable.add(Box.createHorizontalStrut(10)); // 10像素的间距
				tools_enable.add(jcheckbox_debug);
				tools_enable.add(Box.createHorizontalStrut(10)); // 10像素的间距
				tools_enable.add(white_label);
				tools_enable.add(Box.createHorizontalStrut(5)); // 10像素的间距
				tools_enable.add(white_host);

				JButton clear_white_list = new JButton("Clear");
				clear_white_list.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						white_host.setText("*");
						whitelist = new ArrayList<>();
						whitelist.add("*");
						savePersistentSettings();

					}
				});

				//第一行
				matchPanel.add(Utils.Add_Component(gbl,title,gbc_match,0,0,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,tools_enable,gbc_match,1,0,1,1,1,0));
				matchPanel.add(Utils.Add_Component(gbl,clear_white_list,gbc_match,2,0,1,1,0,0));

				matchPanel.add(Utils.Add_Component(gbl,scrollPane,gbc_match,0,1,9,2,1,1));
				matchPanel.add(Utils.Add_Component(gbl,match_add,gbc_match,2,2,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_edit,gbc_match,2,3,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_remove,gbc_match,2,4,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_clear,gbc_match,2,5,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_copy,gbc_match,2,6,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_paste,gbc_match,2,7,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_import,gbc_match,2,8,1,1,0,0));
				matchPanel.add(Utils.Add_Component(gbl,match_export,gbc_match,2,9,1,1,0,0));
				// RED STYLE
				StyleContext styleContext = new StyleContext();
				redStyle = styleContext.addStyle("red", null);
				StyleConstants.setForeground(redStyle, Color.RED);
				// GREEN STYLE
				greenStyle = styleContext.addStyle("green", null);
				StyleConstants.setForeground(greenStyle, Color.GREEN);


				JLabel labelPythonPath = new JLabel("Python path: ");
				pythonPath = new JTextField(200);
				if(callbacks.loadExtensionSetting("pythonPath") != null)
					pythonPath.setText(callbacks.loadExtensionSetting("pythonPath"));
				else {
					if(System.getProperty("os.name").startsWith("Windows")) {
						pythonPath.setText("C:\\python27\\python");
					} else {
						pythonPath.setText("/usr/bin/python");
					}
				}
				pythonPath.setMaximumSize( pythonPath.getPreferredSize() );
				JButton pythonPathButton = new JButton("Select file");
				pythonPathButton.setActionCommand("pythonPathSelectFile");
				pythonPathButton.addActionListener(BurpExtender.this);

				JLabel labelServerStatus = new JLabel("Server status: ");

				documentServerStatus = new DefaultStyledDocument();
				serverStatus = new JTextPane(documentServerStatus);
				serverStatus.setEditable(false);
				try {
					documentServerStatus.insertString(0, "NOT running", redStyle);
				} catch (BadLocationException e) {
					printException(e,"Error setting labels");
				}

				JLabel labelPyroHost = new JLabel("Pyro host: ");
				pyroHost = new JTextField(200);
				if(callbacks.loadExtensionSetting("pyroHost") != null)
					pyroHost.setText(callbacks.loadExtensionSetting("pyroHost"));
				else
					pyroHost.setText("127.0.0.1");
				pyroHost.setMaximumSize( pyroHost.getPreferredSize() );



				JLabel labelPyroPort = new JLabel("Pyro port: ");
				pyroPort = new JTextField(200);
				if(callbacks.loadExtensionSetting("pyroPort") != null)
					pyroPort.setText(callbacks.loadExtensionSetting("pyroPort"));
				else
					pyroPort.setText("19999");
				pyroPort.setMaximumSize( pyroPort.getPreferredSize() );


				JLabel labelBurpyPath = new JLabel("Burpy PY path: ");
				burpyPath = new JTextField(200);
				if(callbacks.loadExtensionSetting("burpyPath") != null)
					burpyPath.setText(callbacks.loadExtensionSetting("burpyPath"));
				else {
					if(System.getProperty("os.name").startsWith("Windows")) {
						burpyPath.setText("C:\\burp\\script.py");
					} else {
						burpyPath.setText("/home/m0nst3r/work/scripts/jnBank.py");
					}
				}
//				burpyPath.setMaximumSize( burpyPath.getPreferredSize() );
				JButton burpyPathButton = new JButton("Select file");
				burpyPathButton.setActionCommand("burpyPathSelectFile");
				burpyPathButton.addActionListener(BurpExtender.this);

				chckbxPro = new JCheckBox("Enable Processor (require processor function)");
				chckbxPro.setEnabled(true);
				chckbxPro.setSelected(callbacks.loadExtensionSetting("enableProcessor") != null && Objects.equals(callbacks.loadExtensionSetting("enableProcessor"), "1"));
				should_pro = (callbacks.loadExtensionSetting("enableProcessor") != null && Objects.equals(callbacks.loadExtensionSetting("enableProcessor"), "1"));

				chckbxPro.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
                        should_pro = chckbxPro.isSelected();
						savePersistentSettings();

					}
				});

				chckbxAuto = new JCheckBox("Enable Auto Enc/Dec (require encrypt and decrypt function)");
				chckbxAuto.setSelected(callbacks.loadExtensionSetting("enableAuto") != null && Objects.equals(callbacks.loadExtensionSetting("enableAuto"), "1"));
				should_auto = (callbacks.loadExtensionSetting("enableAuto") != null && Objects.equals(callbacks.loadExtensionSetting("enableAuto"), "1"));

				chckbxAuto.setEnabled(true);
				chckbxAuto.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
                        should_auto = chckbxAuto.isSelected();
						savePersistentSettings();

					}
				});
				proxy_chckbxAuto = new JCheckBox("Enable Proxy Auto Enc/Dec (require encrypt and decrypt function)");
				proxy_chckbxAuto.setSelected(callbacks.loadExtensionSetting("enableAutoProxy") != null && Objects.equals(callbacks.loadExtensionSetting("enableAutoProxy"), "1"));
				proxy_should_auto = (callbacks.loadExtensionSetting("enableAutoProxy") != null && Objects.equals(callbacks.loadExtensionSetting("enableAutoProxy"), "1"));
				proxy_chckbxAuto.setEnabled(true);
				proxy_chckbxAuto.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
                        proxy_should_auto = proxy_chckbxAuto.isSelected();
						savePersistentSettings();

					}
				});

				JButton startServer = new JButton("Start server");
				startServer.setActionCommand("startServer");
				startServer.addActionListener(BurpExtender.this);

				JButton killServer = new JButton("Kill server");
				killServer.setActionCommand("killServer");
				killServer.addActionListener(BurpExtender.this);

				clearConsoleButton = new JButton("Clear console");
				clearConsoleButton.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								String newConsoleText = "<font color=\"green\">";
								newConsoleText = newConsoleText + "<b>**** Console cleared successfully ****</b><br/>";
								newConsoleText = newConsoleText + "</font><br/>";

								pluginConsoleTextArea.setText(newConsoleText);

							}

						});
					}
				});

				reloadScript = new JButton("Reload Script");
				reloadScript.setActionCommand("reloadScript");
				reloadScript.addActionListener(BurpExtender.this);

				JPanel configPanel = new JPanel();
//				configPanel.setBorder( BorderFactory.createLineBorder(Color.LIGHT_GRAY));
				GridBagLayout gbl_config=new GridBagLayout();//创建网格包布局管理器
				GridBagConstraints gbc_config=new GridBagConstraints();//GridBagConstraints对象来给出每个组件的大小和摆放位置
				configPanel.setLayout(gbl_config);//设置容器布局为网格包布局类型
				gbc_config.fill=GridBagConstraints.BOTH;//组件填充显示区域，当格子有剩余空间时，填充空间
				gbc_config.insets = new Insets(5, 5, 5, 5); // 边距
				gbc_config.anchor = GridBagConstraints.NORTHWEST; // 对齐方式
				//第一行

				configPanel.add(Utils.Add_Component(gbl_config,labelServerStatus,gbc_config,0,0,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,serverStatus,gbc_config,1,0,1,3,1,0));

				//第二行
				configPanel.add(Utils.Add_Component(gbl_config,labelPythonPath,gbc_config,0,1,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,pythonPath,gbc_config,1,1,1,1,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,pythonPathButton,gbc_config,2,1,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,startServer,gbc_config,3,1,1,1,0,0));

				//第三行
				configPanel.add(Utils.Add_Component(gbl_config,labelPyroHost,gbc_config,0,2,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,pyroHost,gbc_config,1,2,1,2,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,killServer,gbc_config,3,2,1,1,0,0));

				//第四行
				configPanel.add(Utils.Add_Component(gbl_config,labelPyroPort,gbc_config,0,3,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,pyroPort,gbc_config,1,3,1,2,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,clearConsoleButton,gbc_config,3,3,1,1,0,0));


				//第五行
				configPanel.add(Utils.Add_Component(gbl_config,labelBurpyPath,gbc_config,0,4,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,burpyPath,gbc_config,1,4,1,1,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,burpyPathButton,gbc_config,2,4,1,1,0,0));
				configPanel.add(Utils.Add_Component(gbl_config,reloadScript,gbc_config,3,4,1,1,0,0));
//
//				6、7、8
				configPanel.add(Utils.Add_Component(gbl_config,chckbxPro,gbc_config,0,5,1,3,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,chckbxAuto,gbc_config,0,6,1,3,1,0));
				configPanel.add(Utils.Add_Component(gbl_config,proxy_chckbxAuto,gbc_config,0,7,1,3,1,0));

				pluginConsoleTextArea = new JEditorPane("text/html", "<font color=\"green\"><b>*** Burpy Console ***</b></font><br/><br/>");
				JScrollPane scrollPluginConsoleTextArea = new JScrollPane(pluginConsoleTextArea);
				scrollPluginConsoleTextArea.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
				pluginConsoleTextArea.setEditable(false);

				JPanel configurationConfPanel = new JPanel();
				GridBagLayout gbl_tab_panel=new GridBagLayout();//创建网格包布局管理器
				GridBagConstraints gbc_tab_panel=new GridBagConstraints();//GridBagConstraints对象来给出每个组件的大小和摆放位置
				configurationConfPanel.setLayout(gbl_tab_panel);//设置容器布局为网格包布局类型
				gbc_tab_panel.fill=GridBagConstraints.BOTH;//组件填充显示区域，当格子有剩余空间时，填充空间
				gbc_tab_panel.insets = new Insets(0, 0, 0, 0); // 边距
				gbc_tab_panel.anchor = GridBagConstraints.NORTHWEST; // 对齐方式
				//第一行
				configurationConfPanel.add(Utils.Add_Component(gbl_tab_panel,matchPanel,gbc_tab_panel,0,0,1,1,1,0));
				configurationConfPanel.add(Utils.Add_Component(gbl_tab_panel,configPanel,gbc_tab_panel,0,1,1,1,1,0));
				configurationConfPanel.add(Utils.Add_Component(gbl_tab_panel,scrollPluginConsoleTextArea,gbc_tab_panel,0,2,1,1,1,1));

				tabbedPanel.add("Configurations",configurationConfPanel);
				mainPanel = new JPanel();
				mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
				mainPanel.add(tabbedPanel);
				callbacks.customizeUiComponent(mainPanel);
				callbacks.addSuiteTab(BurpExtender.this);
			}

		});
		callbacks.registerHttpListener(this);
	}

	@SuppressWarnings("unchecked")
	public void getMethods() {
			try {
//				service = new PyroProxy(serviceHost, servicePort, serviceObj);
				this.burpyMethods = (List<String>) (pyroBurpyService.call("get_methods"));
//				printSuccessMessage(this.burpyMethods.toString());
//				stdout.println(pyroBurpyService.pyroMethods);
			} catch (Exception e) {
				stderr.println(e);
				StackTraceElement[] exceptionElements = e.getStackTrace();
                for (StackTraceElement exceptionElement : exceptionElements) {
                    stderr.println(exceptionElement.toString());
                }
			}finally {
				if (this.burpyMethods != null) {
					printSuccessMessage("methods loaded");
				}else{
					stdout.println("Methods loading failed");
				}
			}

	}

	private void launchPyroServer(String pythonPath, String pyroServicePath) {

		Runtime rt = Runtime.getRuntime();

		serviceHost = pyroHost.getText().trim();
		servicePort = Integer.parseInt(pyroPort.getText().trim());

		String[] startServerCommand = {pythonPath,"-i",pyroServicePath,serviceHost,Integer.toString(servicePort),burpyPath.getText().trim()};


		try {
//			documentServerStatus.insertString(0, "starting up... ", redStyle);
			pyroServerProcess = rt.exec(startServerCommand);
			final BufferedReader stdOutput = new BufferedReader(new InputStreamReader(pyroServerProcess.getInputStream()));
			final BufferedReader stdError = new BufferedReader(new InputStreamReader(pyroServerProcess.getErrorStream()));
			// Initialize thread that will read stdout
			stdoutThread = new Thread()  {
				public void run()  {
				while(thread_stout_flag && !Thread.currentThread().isInterrupted()) {
					try {
						final String line = stdOutput.readLine();
						// Only used to handle Pyro first message (when server start)
//							if(line.equals("Ready.")) {
						if(line.contains("running") || line.startsWith("Ready.")) {
							pyroBurpyService = new PyroProxy(serviceHost,servicePort,serviceObj);
							serverStarted = true;
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									serverStatus.setText("");
									try {
										documentServerStatus.insertString(0, "running", greenStyle);
//											documentServerStatusButtons.insertString(0, "Server running", greenStyle);
									} catch (BadLocationException e) {

										printException(e,"Exception setting labels");
									}

								}
							});

							printSuccessMessage("Pyro server started correctly");
							printSuccessMessage("Better use \"Kill Server\" after finished!");
							printSuccessMessage("Analyzing scripts");
							getMethods();
							// Standard line
						} else {
							printJSMessage(line);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
				}
			};
			thread_error_flag=true;
			thread_stout_flag=true;
			stdoutThread.start();
			// Initialize thread that will read stderr
			stderrThread = new Thread() {

				public void run() {
					while(thread_error_flag && !Thread.currentThread().isInterrupted()) {
						try {

							final String line = stdError.readLine();
							printException(null,line);

						} catch (IOException e) {

							printException(e,"Error reading Pyro stderr");
						}
					}
				}

			};
			stderrThread.start();

		} catch (final Exception e1) {

			printException(e1,"Exception starting Pyro server");

		}
	}

	@Override
	public String getTabCaption() {

		return ExtenderName;
	}
	@Override
	public Component getUiComponent() {
		return mainPanel;
	}
	static void savePersistentSettings() {
		try{
			callbacks.saveExtensionSetting("match_replace_enable", checkbox_enable_match_repalce.isSelected() ?"1":"0");
			callbacks.saveExtensionSetting("match_replace_enable_proxy",checkbox_enable_match_repalce_proxy.isSelected() ?"1":"0");
			callbacks.saveExtensionSetting("match_replace_enable_repeater",checkbox_enable_match_repalce_repeater.isSelected() ?"1":"0");
			callbacks.saveExtensionSetting("match_replace_enable_extender",checkbox_enable_match_repalce_EXTENDER.isSelected() ?"1":"0");
			callbacks.saveExtensionSetting("is_debug",is_debug ?"1":"0");
			callbacks.saveExtensionSetting("whiteList",white_host.getText());

			callbacks.saveExtensionSetting("pythonPath",pythonPath.getText().trim());
			callbacks.saveExtensionSetting("pyroHost",pyroHost.getText().trim());
			callbacks.saveExtensionSetting("pyroPort",pyroPort.getText().trim());
			callbacks.saveExtensionSetting("burpyPath",burpyPath.getText().trim());

			callbacks.saveExtensionSetting("enableProcessor",chckbxPro.isSelected()?"1":"0");
			callbacks.saveExtensionSetting("enableAuto",chckbxAuto.isSelected()?"1":"0");
			callbacks.saveExtensionSetting("enableAutoProxy",proxy_chckbxAuto.isSelected()?"1":"0");
			List<DataEntry> all_data = table_model.getAllValue();
			callbacks.saveExtensionSetting("matchReplaceTable",JsontoStr(all_data));
		}catch (Exception e){
			printException(e,"Exception saving settings");
		}
	}




	@Override
	public void actionPerformed(ActionEvent event) {
		String command = event.getActionCommand();
		if(command.equals("killServer") && serverStarted) {
			thread_error_flag=false;
			thread_stout_flag=false;
			stdoutThread.interrupt();
			stderrThread.interrupt();
//			stdoutThread.stop();
//			stderrThread.stop();
			try {
//				pyroBurpyService.close("shutdown");
//				pyroServerProcess.destroy();
				pyroServerProcess.destroy();
				pyroServerProcess.destroyForcibly();
				pyroBurpyService.close();
				serverStarted = false;
				serverStatus.setText("");
//				serverStatusButtons.setText("");
				try {
					documentServerStatus.insertString(0, "NOT running", redStyle);
//					documentServerStatusButtons.insertString(0, "Server stopped", redStyle);
				} catch (BadLocationException e) {
					printException(e,"Exception setting labels");
				}
				printSuccessMessage("Pyro server shutted down");

			} catch (final Exception e) {
				printException(e,"Exception shutting down Pyro server");
			}
		} else if(command.equals("pythonPathSelectFile") && !serverStarted) {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);//设置只能选择目录
			int returnVal = chooser.showOpenDialog(BurpExtender.this);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				String selectPath =chooser.getSelectedFile().getPath() ;
				pythonPath.setText(selectPath);
				chooser.setVisible(false);
			}
		} else if(command.equals("burpyPathSelectFile") && !serverStarted) {
			JFileChooser chooser = new JFileChooser();
			// 创建一个文件过滤器，仅显示 .txt 文件
			FileNameExtensionFilter pyFileFilter = new FileNameExtensionFilter("Python files", "py");
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);//设置只能选择目录
			chooser.addChoosableFileFilter(pyFileFilter);
			int returnVal = chooser.showOpenDialog(BurpExtender.this);
			if(returnVal == JFileChooser.APPROVE_OPTION) {
				String selectPath =chooser.getSelectedFile().getPath() ;
				burpyPath.setText(selectPath);
				chooser.setVisible(false);
			}
		} else if(command.equals("startServer") && !serverStarted) {

			File burpyFile = new File(burpyPath.getText().trim());
			if (burpyFile.exists()) {
				savePersistentSettings();
				try {
					launchPyroServer(pythonPath.getText().trim(), pythonScript);
				} catch (final Exception e) {
					printException(null, "Exception starting Pyro server");
				}
			}else {
				printException(null,"Python File not found!");

			}

		} else if (burpyMethods.contains(command)) {
			IHttpRequestResponse[] selectedItems = currentInvocation.getSelectedMessages();
			byte selectedInvocationContext = currentInvocation.getInvocationContext();

			try {
				// pass directly the bytes of http
				byte[] selectedRequestOrResponse;
				if(selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST || selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST) {
					selectedRequestOrResponse = selectedItems[0].getRequest();
				} else {
					selectedRequestOrResponse = selectedItems[0].getResponse();
				}

				String ret_str = (String) pyroBurpyService.call("invoke_method", command, helpers.base64Encode(selectedRequestOrResponse));

				if(selectedInvocationContext == IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST) {
					selectedItems[0].setRequest(ret_str.getBytes());
				} else {

					final String msg = ret_str.substring(ret_str.indexOf("\r\n\r\n")+4);
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							MessageDialog.show("Burpy "+command, msg);
						}
					});
				}

			} catch (Exception e) {

				printException(e, "Exception with custom context application");

			}

		} else if(command.startsWith("reloadScript")){
			if(serverStarted) {
				thread_error_flag=false;
				thread_stout_flag=false;
				stdoutThread.interrupt();
				stderrThread.interrupt();
//				stdoutThread.stop();
//				stderrThread.stop();

				try {
					thread_error_flag=true;
					thread_stout_flag=true;
					pyroServerProcess.destroyForcibly();
					pyroBurpyService.close();
					serverStarted = false;

					serverStatus.setText("");
					try {
						documentServerStatus.insertString(0, "NOT running", redStyle);
					} catch (BadLocationException e) {
						printException(e, "Exception setting labels");
					}
					printSuccessMessage("Pyro server shutted down");
				} catch (final Exception e) {
					printException(e, "Exception shutting down Pyro server");
				}
			}
			try {

				launchPyroServer(pythonPath.getText().trim(),pythonScript);
				getMethods();
			} catch (final Exception e) {
				printException(null,"Exception starting Pyro server");
			}
		}
	}

	@Override
	public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
			currentInvocation = invocation;
			List<JMenuItem> menu = new ArrayList<>();
			this.burpyMethods.forEach(method_name -> {
				JMenuItem item = new JMenuItem("Burpy " + method_name);
				item.setActionCommand(method_name);
				item.addActionListener(this);
				menu.add(item);
			});

			return menu;

	}

//	static String byteArrayToHexString(byte[] raw) {
//		StringBuilder sb = new StringBuilder(2 + raw.length * 2);
//		for (int i = 0; i < raw.length; i++) {
//			sb.append(String.format("%02X", Integer.valueOf(raw[i] & 0xFF)));
//		}
//		return sb.toString();
//	}

//	private static byte[] hexStringToByteArray(String hex) {
//		byte[] b = new byte[hex.length() / 2];
//		for (int i = 0; i < b.length; i++){
//			int index = i * 2;
//			int v = Integer.parseInt(hex.substring(index, index + 2), 16);
//			b[i] = (byte)v;
//		}
//		return b;
//	}
//	static String strToHexStr(String str) {
//		char[] chars = "0123456789ABCDEF".toCharArray();
//		StringBuilder sb = new StringBuilder("");
//		byte[] bs = str.getBytes();
//		int bit;
//		for (int i=0; i<bs.length; i++) {
//			bit = (bs[i] & 0x0f0) >>4;
//			sb.append(chars[bit]);
//			bit = bs[i] & 0x0f;
//			sb.append(chars[bit]);
//		}
//		return sb.toString().trim();
//
//	}
	@Override
	public void mouseClicked(MouseEvent e) {

	}
	@Override
	public void mousePressed(MouseEvent e) {

	}

	@Override
	public void mouseReleased(MouseEvent e) {

	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	public static void printSuccessMessage(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {

				String oldConsoleText = pluginConsoleTextArea.getText();

				Pattern p = Pattern.compile("^.*<body>(.*)</body>.*$", Pattern.DOTALL);
				Matcher m = p.matcher(oldConsoleText);

				String newConsoleText = "";
				if(m.find()) {
					newConsoleText = m.group(1);
				}

				if(lastPrintIsJS) {
					newConsoleText = newConsoleText + "<br/>";
				}
				newConsoleText = newConsoleText + "<font color=\"green\">";
				newConsoleText = newConsoleText + "<b>" + message + "</b><br/>";
				newConsoleText = newConsoleText + "</font><br/>";
				pluginConsoleTextArea.setText(newConsoleText);
				lastPrintIsJS = false;
			}
		});

	}
	public static void printDebugMessage(final String message) {
		if(!is_debug){
			return;
		}
		stdout.println(message);
	}

	public void printJSMessage(final String message) {

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				String oldConsoleText = pluginConsoleTextArea.getText();
				Pattern p = Pattern.compile("^.*<body>(.*)</body>.*$", Pattern.DOTALL);
				Matcher m = p.matcher(oldConsoleText);

				String newConsoleText = "";
				if(m.find()) {
					newConsoleText = m.group(1);
				}

				newConsoleText = newConsoleText + "<font color=\"black\"><pre>";
				//newConsoleText = newConsoleText + message + "<br/>";
				newConsoleText = newConsoleText + message;
				newConsoleText = newConsoleText + "</pre></font>";

				pluginConsoleTextArea.setText(newConsoleText);

				lastPrintIsJS = true;
			}
		});

	}


	public static void printException(final Exception e, final String message) {
		if(message==null || message.isEmpty()){
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				String oldConsoleText = pluginConsoleTextArea.getText();
				Pattern p = Pattern.compile("^.*<body>(.*)</body>.*$", Pattern.DOTALL);
				Matcher m = p.matcher(oldConsoleText);

				StringBuilder newConsoleText = new StringBuilder();
				if(m.find()) {
					newConsoleText = new StringBuilder(m.group(1));
				}

				if(lastPrintIsJS) {
					newConsoleText.append("<br/>");
				}

				newConsoleText.append("<font color=\"red\">");
				newConsoleText.append("<b>").append(message).append("</b><br/>");

				if(e != null) {
					newConsoleText.append(e).append("<br/>");
					//consoleText = consoleText + e.getMessage() + "<br/>";
					StackTraceElement[] exceptionElements = e.getStackTrace();
                    for (StackTraceElement exceptionElement : exceptionElements) {
                        newConsoleText.append(exceptionElement.toString()).append("<br/>");
                    }
				}
				newConsoleText.append("</font><br/>");
				pluginConsoleTextArea.setText(newConsoleText.toString());
				lastPrintIsJS = false;
			}

		});

	}


	@Override
	public void extensionUnloaded() {

		if(serverStarted) {
			thread_error_flag=false;
			thread_stout_flag=false;
			stdoutThread.interrupt();
			stderrThread.interrupt();
//			stdoutThread.stop();
//			stderrThread.stop();

			try {
                pyroBurpyService.call("shutdown");
				pyroServerProcess.destroyForcibly();
				pyroBurpyService.close();

				printSuccessMessage("Pyro server shutted down");

			} catch (final Exception e) {

				printException(e,"Exception shutting down Pyro server");

			}
		}

	}

	@Override
	public String getProcessorName()
	{
		return "Burpy processor";
	}

	@Override
	public byte[] processPayload(byte[] currentPayload, byte[] originalPayload, byte[] baseValue)
	{
		byte[] ret = currentPayload;
		if(should_pro){

			try {
				final String s = (String) (pyroBurpyService.call("invoke_method", "processor", new String(currentPayload)));
				ret = s.getBytes();
			} catch (Exception e) {
				stderr.println(e);
				StackTraceElement[] exceptionElements = e.getStackTrace();
                for (StackTraceElement exceptionElement : exceptionElements) {
                    stderr.println(exceptionElement.toString());
                }
			}
		}

		return ret;
	}


	@Override
	public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
		String host =  messageInfo.getHttpService().getHost();
//		printDebugMessage(whitelist.toString());
		if (!whitelist.contains(host) && !whitelist.contains("*")) {
			printDebugMessage("Domain name mismatch:"+host);
			return;
		}
		if(is_match_replace){
			if(toolFlag == IBurpExtenderCallbacks.TOOL_PROXY && !is_match_replace_proxy){
				return;
			}
			if(toolFlag == IBurpExtenderCallbacks.TOOL_REPEATER && !is_match_replace_repeater){
				return;
			}
			if(toolFlag == IBurpExtenderCallbacks.TOOL_EXTENDER && !is_match_replace_other){
				return;
			}
			String request_header;
			String request_body;
			String response_header;
			String response_body;
			if (messageIsRequest) {
				byte[] rq=messageInfo.getRequest();
				IRequestInfo analyzerq=helpers.analyzeRequest(rq);
				int bodyOffset = analyzerq.getBodyOffset();
				request_body = new String(messageInfo.getRequest()).substring(bodyOffset);
				request_header=String.join("\r\n", analyzerq.getHeaders());
				for (DataEntry data : table_model.getAllValue()) {
					if (!data.enable) {
						continue;
					}

					switch (data.item) {
						case "Request header":
							request_header = handleData(data,request_header);
							continue;
						case "Request body":
							request_body =handleData(data, request_body);
							continue;
						default:
							continue;
					}
				}
				List<String> request_header_list = Arrays.asList(request_header.split("\r\n|\n|\r"));
				byte[] newRequest = helpers.buildHttpMessage(request_header_list, request_body.getBytes());
				messageInfo.setRequest(newRequest);
				printDebugMessage("Request:\n"+request_header+"\n\n"+request_body);
			} else {
				byte[] rp=messageInfo.getResponse();
				IResponseInfo analyzeResponse=helpers.analyzeResponse(rp);
				int bodyOffset = analyzeResponse.getBodyOffset();
				response_body = new String(messageInfo.getResponse()).substring(bodyOffset);
				response_header=String.join("\r\n", analyzeResponse.getHeaders());

				for (DataEntry data : table_model.getAllValue()) {
					if (!data.enable) {
						continue;
					}
					switch (data.item) {
						case "Response header":
							response_header =handleData(data, response_header);
							continue;
						case "Response body":
							response_body =handleData(data, response_body);
							continue;
						default:
							continue;
					}
				}
				List<String> response_header_list = Arrays.asList(response_header.split("\r\n|\n|\r"));
				byte[] newResponse = helpers.buildHttpMessage(response_header_list, response_body.getBytes());
				messageInfo.setResponse(newResponse);
				printDebugMessage("Response:\n"+response_header+"\n\n"+response_body);
			}
//			printException(null,"End Replace");
		}
//		printSuccessMessage(new String(messageInfo.getRequest()));
//		printSuccessMessage(encodeHtml(new String(messageInfo.getResponse())));
		List<String> headers;
		if (should_auto) {
			if(toolFlag == IBurpExtenderCallbacks.TOOL_PROXY){
				if(!proxy_should_auto){
					return;
				}
			}
//			if (toolFlag == IBurpExtenderCallbacks.TOOL_SCANNER ||
//					toolFlag == IBurpExtenderCallbacks.TOOL_REPEATER ||
//					toolFlag == IBurpExtenderCallbacks.TOOL_INTRUDER) {
			if (messageIsRequest) {

				byte[] request = messageInfo.getRequest();

					String ret = "";
					try {
						ret = (String) pyroBurpyService.call("invoke_method", "encrypt", helpers.base64Encode(request));
					} catch(Exception e) {
						stderr.println(e);
						StackTraceElement[] exceptionElements = e.getStackTrace();
                        for (StackTraceElement exceptionElement : exceptionElements) {
                            stderr.println(exceptionElement.toString());
                        }
					}

					IRequestInfo nreqInfo = helpers.analyzeRequest(ret.getBytes());
					headers = nreqInfo.getHeaders();
					int nbodyOff = nreqInfo.getBodyOffset();
					byte[] nbody = ret.substring(nbodyOff).getBytes();

					byte[] newRequest = helpers.buildHttpMessage(headers, nbody); //

					messageInfo.setRequest(newRequest);

			}else {
				byte[] response = messageInfo.getResponse();
				String ret = "";
				try {
					ret = (String) pyroBurpyService.call("invoke_method", "decrypt", helpers.base64Encode(response));
					stderr.println(ret);
				} catch(Exception e) {
					stderr.println(e);
					StackTraceElement[] exceptionElements = e.getStackTrace();
                    for (StackTraceElement exceptionElement : exceptionElements) {
                        stderr.println(exceptionElement.toString());
                    }
				}
				IResponseInfo nresInfo = helpers.analyzeResponse(ret.getBytes());
				int nbodyOff = nresInfo.getBodyOffset();
				byte[] nbody = ret.substring(nbodyOff).getBytes();
				headers = nresInfo.getHeaders();
				byte[] newResponse = helpers.buildHttpMessage(headers, nbody);
				messageInfo.setResponse(newResponse);
			}
		}
	}

	@Override
	public IMessageEditorTab createNewInstance(IMessageEditorController controller, boolean editable) {
		return new iMessageEditorTab(controller, editable);
	}

	public class iMessageEditorTab implements IMessageEditorTab {
        private final ITextEditor iTextEditor = callbacks.createTextEditor();
		private byte[] currentMessage;
		public iMessageEditorTab(IMessageEditorController controller, boolean editable) {
        }
		@Override
		public String getTabCaption() {
			return "BurpyTab";
		}
		@Override
		public Component getUiComponent() {
			return iTextEditor.getComponent();
		}

		@Override
		public boolean isEnabled(byte[] content, boolean isRequest) {
			return true;
		}

		@Override
		public void setMessage(byte[] content, boolean isRequest) {
			String ret = "";
			try {
				ret = (String) pyroBurpyService.call("invoke_method", "decrypt", helpers.base64Encode(content));
			} catch(Exception e) {
				stderr.println(e);
				StackTraceElement[] exceptionElements = e.getStackTrace();
                for (StackTraceElement exceptionElement : exceptionElements) {
                    stderr.println(exceptionElement.toString());
                }
			}
			iTextEditor.setText(ret.getBytes(StandardCharsets.UTF_8));

			currentMessage = ret.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public byte[] getMessage() {
			if (iTextEditor.isTextModified()){
				byte[] data = iTextEditor.getText();
				String ret = "";
				try {
					ret = (String) pyroBurpyService.call("invoke_method", "encrypt", helpers.base64Encode(data));
				} catch(Exception e) {
					stderr.println(e);
					StackTraceElement[] exceptionElements = e.getStackTrace();
                    for (StackTraceElement exceptionElement : exceptionElements) {
                        stderr.println(exceptionElement.toString());
                    }
				}

				return ret.getBytes(StandardCharsets.UTF_8);
			} else {
				return currentMessage;
			}
		}

		@Override
		public boolean isModified() {
			return iTextEditor.isTextModified();
		}

		@Override
		public byte[] getSelectedData() {
			return iTextEditor.getSelectedText();
		}
	}

	public static void main(String[] args) throws PyroException {
		// for testing purpose
		System.out.println("Initializing service");
//		NameServerProxy ns = NameServerProxy.locateNS(null);
		PyroProxy service = null;
		try {
			service = new PyroProxy("127.0.0.1", 10999, "BurpyServicePyro");
		}catch (PyroException | IOException e) {
			e.printStackTrace();
		}
        System.out.println("Getting methods");
		try {
			Object methods_obj = service.call("get_methods");
			System.out.println(methods_obj.toString());
		}catch (PyroException e) {
			System.out.println("PyroException");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

