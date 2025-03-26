package org.example;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class MainView extends JFrame {
    private JTable table;
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JMenuBar menuBar = new JMenuBar();
    private final JMenu fileMenu = new JMenu("文件");
    private final JMenu editMenu = new JMenu("编辑");
    private final JMenuItem find = new JMenuItem("查找");
    private final JMenuItem ultrafind = new JMenuItem("高阶查找");
    private final JMenuItem imp = new JMenuItem("导入");
    private final JMenuItem open = new JMenuItem("打开");
    private final JMenuItem newDB = new JMenuItem("新建"); // 新增：新建数据库选项
    private final JMenuItem add = new JMenuItem("添加");
    private final JMenuItem export = new JMenuItem("导出"); // 新增：导出数据选项

    private final JPanel navigationPanel = new JPanel();
    private final JLabel pageLabel = new JLabel();
    private final JButton prevButton = new JButton("上一页");
    private final JButton nextButton = new JButton("下一页");

    private int currentPage = 1;
    private int totalPages = 1;
    private final int pageSize = 35; // 每页记录数
    private int totalRecords = 0;
    private String currentTable = null;

    private String currentDBPath = null; // 当前数据库路径
    private DOperator operater = null; // 当前数据库操作对象

    // 新增：数据表浏览窗口（使用 JList）
    private JList<String> dataList;
    private DefaultListModel<String> listModel = new DefaultListModel<>();

    // 新增：用于跟踪当前是否在搜索模式以及存储搜索参数
    private String[] currentSearchFields = null;
    private String[] currentSearchValues = null;
    LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainView a = new MainView();
            a.DBMSInterface();
        });
    }

    public void DBMSInterface() {
        setTitle("Java 大作业");
        setSize(1200, 700); // 增加宽度以容纳左侧浏览窗口
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        initializeMenuBar();
        initializeTable();  // 初始化表格
        initializeNavigationPanel();
        initializeDataList(); // 初始化左侧数据表浏览窗口
        registerCtrlCAction(); // 注册全局 Ctrl+C 事件

        // 使用 JSplitPane 将左侧浏览窗口和右侧表格分开
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(dataList), new JScrollPane(table));
        splitPane.setDividerLocation(200); // 设置分割条初始位置

        add(menuBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(navigationPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void initializeMenuBar() {
        newDB.addActionListener(e -> createNewDatabase());
        open.addActionListener(e -> openDatabase());
        imp.addActionListener(e -> importTSV());
        find.addActionListener(e -> {
            try {
                findData();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
        add.addActionListener(e -> addData());  // 注册“添加”功能
        export.addActionListener(e -> exportData()); // 注册“导出”功能
        ultrafind.addActionListener(e -> {
            try {
                performUltraFind();
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "高阶查找失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        // 组装菜单
        fileMenu.add(newDB); // 添加“新建”选项到“文件”菜单
        fileMenu.add(open);
        fileMenu.add(imp);
        fileMenu.add(export); // 添加“导出”选项到“文件”菜单
        editMenu.add(add); // 将“添加”项添加到编辑菜单
        editMenu.add(find);
        editMenu.add(ultrafind);
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
    }

    private void initializeTable() {
        // 初始表头可以根据导入的数据动态设置，这里设置为空
        tableModel.setColumnIdentifiers(new String[]{}); // 初始化表头为空
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    private void initializeNavigationPanel() {
        prevButton.addActionListener(e -> changePage(-1));
        nextButton.addActionListener(e -> changePage(1));
        navigationPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        prevButton.setPreferredSize(new Dimension(100, 30));
        nextButton.setPreferredSize(new Dimension(100, 30));
        pageLabel.setPreferredSize(new Dimension(100, 30));
        navigationPanel.add(prevButton);
        navigationPanel.add(pageLabel);
        navigationPanel.add(nextButton);
        updatePageLabel();
        pageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR)); // 设置鼠标悬停时的指针样式
        pageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPageJumpDialog();
            }
        });

    }
    /**
     * 显示页面跳转对话框，让用户输入目标页码。
     */
    private void showPageJumpDialog() {
        String input = JOptionPane.showInputDialog(this, "请输入要跳转的页码（1 - " + totalPages + "）：", "页面跳转", JOptionPane.PLAIN_MESSAGE);

        if (input != null) {
            try {
                int targetPage = Integer.parseInt(input.trim());
                if (targetPage < 1 || targetPage > totalPages) {
                    JOptionPane.showMessageDialog(this, "请输入一个有效的页码（1 - " + totalPages + "）！", "输入错误", JOptionPane.ERROR_MESSAGE);
                } else if (targetPage == currentPage) {
                    JOptionPane.showMessageDialog(this, "您当前已经在第 " + targetPage + " 页。", "信息", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    currentPage = targetPage;
                    updateTable();
                    updatePageLabel();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "请输入一个有效的数字！", "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void addData() {
        if (currentTable == null || operater == null) {
            JOptionPane.showMessageDialog(this, "请先打开数据库并选择数据表！", "操作错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 获取当前表的所有列名
        Vector<String> columnNames = operater.getTableColumnNames(currentTable);
        if (columnNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前表没有列可供添加数据！", "信息", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 提示用户逐列输入数据
        StringBuilder message = new StringBuilder("请输入每一列的数据，用逗号分隔：\n");
        for (String columnName : columnNames) {
            message.append(columnName).append("\n");
        }

        // 弹出输入框，用户输入的列数据
        String input = JOptionPane.showInputDialog(this, message.toString());

        if (input == null || input.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "输入不能为空！", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 分割输入的值，假设用逗号分隔
        String[] values = input.split(",");
        if (values.length != columnNames.size()) {
            JOptionPane.showMessageDialog(this, "输入的数据列数与表列数不匹配！", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 去除每个值的前后空白字符
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
        }

        // 将数据插入数据库
        try {
            operater.insert(currentTable, values);
            JOptionPane.showMessageDialog(this, "数据插入成功！");
            // 如果在搜索模式下，重新执行搜索以反映最新数据
            if (currentSearchFields != null && currentSearchValues != null) {
                performSearch(currentSearchFields, currentSearchValues);
            } else {
                updateTable();
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "插入数据失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void changePage(int delta) {
        int newPage = currentPage + delta;
        if (newPage < 1 || newPage > totalPages) {
            return;
        }
        currentPage = newPage;
        updateTable();
        updatePageLabel();
    }

    /**
     * 创建新数据库文件的功能。
     */
    private void createNewDatabase() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("新建数据库文件");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("SQLite Database Files (*.db)", "db");
        fileChooser.addChoosableFileFilter(filter);

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String dbPath = selectedFile.getAbsolutePath();

            // 确保文件以 .db 结尾
            if (!dbPath.toLowerCase().endsWith(".db")) {
                dbPath += ".db";
                selectedFile = new File(dbPath);
            }

            // 检查文件是否已存在
            if (selectedFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "确认覆盖", JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // 创建数据库
            DOperator newOperater = new DOperator(dbPath);
            newOperater.createDatabase(); // 假设 createDatabase() 方法为 void
            operater = newOperater; // 更新当前数据库操作对象
            currentDBPath = dbPath;
            setTitle("Java 大作业 - " + currentDBPath);
            JOptionPane.showMessageDialog(this, "新数据库文件已创建: " + dbPath);
            listModel.clear(); // 清空数据表浏览列表
            tableModel.setRowCount(0); // 清空表格数据
            tableModel.setColumnCount(0); // 清空表格列
            loadTables(); // 加载新数据库中的表
            if (!listModel.isEmpty()) {
                dataList.setSelectedIndex(0); // 自动选择第一个表
            }
            currentSearchFields = null; // 重置搜索参数
            currentSearchValues = null;
        }
    }

    /**
     * 打开现有的数据库文件，仅允许选择 .db 文件。
     */
    private void openDatabase() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("打开数据库文件");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("SQLite Database Files (*.db)", "db");
        fileChooser.addChoosableFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String dbPath = selectedFile.getAbsolutePath();

            // 检查文件后缀名是否为 .db
            if (!dbPath.toLowerCase().endsWith(".db")) {
                JOptionPane.showMessageDialog(this, "请选择后缀名为 .db 的文件！", "文件类型错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            operater = new DOperator(dbPath);
            currentDBPath = dbPath;
            setTitle("Java 大作业 - " + currentDBPath);
            JOptionPane.showMessageDialog(this, "成功打开数据库文件: " + dbPath);
            loadTables(); // 加载数据库中的所有表到数据表浏览列表
            currentSearchFields = null; // 重置搜索参数
            currentSearchValues = null;
        }
    }

    /**
     * 加载当前数据库中的所有表到数据表浏览列表。
     */
    private void loadTables() {
        listModel.clear();
        if (operater == null || currentDBPath == null) {
            return;
        }

        List<String> tableNames = operater.getAllTableNames();
        if (tableNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前数据库中没有任何表。", "信息", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        for (String tableName : tableNames) {
            listModel.addElement(tableName);
        }

        // 自动选择第一个表以加载数据
        if (!tableNames.isEmpty()) {
            dataList.setSelectedIndex(0);
        }

    }

    /**
     * 导入 TSV 文件到当前数据库。
     */
    private void importTSV() {
        if (operater == null || currentDBPath == null) {
            JOptionPane.showMessageDialog(this, "请先打开一个数据库文件！", "操作错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择要导入的 TSV 文件");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true); // 允许多文件选择
        FileNameExtensionFilter filter = new FileNameExtensionFilter("TSV Files (*.tsv)", "tsv");
        fileChooser.addChoosableFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File[] tsvFiles = fileChooser.getSelectedFiles(); // 获取多个文件

        for (File tsvFile : tsvFiles) {
            String tsvFilePath = tsvFile.getAbsolutePath();

            if (!tsvFilePath.toLowerCase().endsWith(".tsv")) {
                JOptionPane.showMessageDialog(this, "请选择后缀名为 .tsv 的文件！", "文件类型错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String defaultTableName = tsvFile.getName().substring(0, tsvFile.getName().lastIndexOf('.'));
            String tableName = JOptionPane.showInputDialog(this, "请输入导入后的表名：", defaultTableName);

            if (tableName == null || tableName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "表名不能为空！", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            tableName = tableName.trim();

            if (!isValidTableName(tableName)) {
                JOptionPane.showMessageDialog(this, "表名不合法！请使用字母、数字和下划线，且以字母或下划线开头。", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ImportProgressDialog progressDialog = new ImportProgressDialog(this, "导入中...", "正在导入数据，请稍候...");

            ImportProgressCallback callback = (total, processed) -> {
                SwingUtilities.invokeLater(() -> progressDialog.updateProgress(processed, total));
            };

            TSVImporter importer = new TSVImporter(operater, hasHeader(), callback);

            String finalTableName = tableName;
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    try {
                        importer.importTSV(finalTableName, tsvFile);
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(MainView.this, "导入失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            e.printStackTrace();
                        });
                    }
                    return null;
                }

                @Override
                protected void done() {
                    progressDialog.dispose();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(MainView.this, "TSV 文件导入成功！");
                        loadTables();
                        if (finalTableName.equals(currentTable)) {
                            currentPage = 1;
                            totalRecords = operater.getTotalRecords(currentTable);
                            totalPages = (int) Math.ceil((double) totalRecords / pageSize);
                            if (totalPages == 0) totalPages = 1;
                            updateTable();
                            updatePageLabel();
                        }
                    });
                }
            };

            worker.execute();
            progressDialog.setVisible(true);
        }
    }

    /**
     * 检查表名是否合法。
     * SQLite 表名必须以字母或下划线开头，可以包含字母、数字和下划线。
     *
     * @param tableName 要验证的表名。
     * @return 如果合法，返回 true；否则返回 false。
     */
    private boolean isValidTableName(String tableName) {
        return tableName.matches("^[A-Za-z_][A-Za-z0-9_]*$");
    }

    /**
     * 判断当前 TSV 文件是否包含表头。
     * 可以根据实际需求修改此方法，比如提供一个选项让用户选择。
     *
     * @return 如果包含表头，返回 true；否则返回 false。
     */
    private boolean hasHeader() {
        // 这里默认返回 true，实际可以通过用户选项或其他方式确定
        return true;
    }

    /**
     * 查找数据的方法。
     */
    private void findData() throws SQLException {
        if (currentTable == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个数据表！", "操作错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 获取当前表的所有列名
        Vector<String> columnNames = operater.getTableColumnNames(currentTable);
        if (columnNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前表没有列可供搜索！", "信息", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 弹出搜索对话框
        SearchDialog searchDialog = new SearchDialog(this, "查找数据", columnNames);
        searchDialog.setVisible(true);

        if (searchDialog.isConfirmed()) {
            String selectedColumn = searchDialog.getSelectedColumn();
            String keyword = searchDialog.getKeyword();
            if (selectedColumn != null && !selectedColumn.trim().isEmpty() &&
                    keyword != null && !keyword.trim().isEmpty()) {
                String[] fields = {selectedColumn.trim()};
                String[] values = {"%" + keyword.trim() + "%"}; // 使用 LIKE 进行模糊匹配
                performSearch(fields, values);
            } else {
                JOptionPane.showMessageDialog(this, "字段和关键字不能为空！", "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 执行搜索操作，根据选定字段和关键字进行模糊匹配。
     *
     * @param fieldNames   要搜索的字段名数组。
     * @param searchValues 搜索关键字数组。
     */
    private void performSearch(String[] fieldNames, String[] searchValues) throws SQLException {
        // 执行搜索
        SelectResult searchResult = null;
        try {
            searchResult = operater.select(currentTable, fieldNames, searchValues, 1, pageSize);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "搜索失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return;
        }

        // 更新表格显示搜索结果
        updateTableWithDatabaseData(searchResult.getColumnNames(), searchResult.getDataRows());

        // 更新分页信息
        currentPage = 1;
        totalRecords = operater.getTotalRecordsWithSearch(currentTable, fieldNames, searchValues); // 确保 DOperator 中已实现此方法
        totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        if (totalPages == 0) totalPages = 1;
        updatePageLabel();

        // 设置当前搜索参数
        currentSearchFields = fieldNames;
        currentSearchValues = searchValues;
    }

    // 注册Ctrl+C和Ctrl+F事件
    private void registerCtrlCAction() {
        Action copyAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyToClipboard();
            }
        };
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "copy-action");
        actionMap.put("copy-action", copyAction);

        // 绑定 Ctrl+F 快捷键到查找功能
        Action findAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    findData();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "find-action");
        actionMap.put("find-action", findAction);
    }

    // 复制表格数据到剪贴板
    private void copyToClipboard() {
        int[] selectedRows = table.getSelectedRows();
        int[] selectedColumns = table.getSelectedColumns();
        if (selectedRows.length == 0 || selectedColumns.length == 0) {
            JOptionPane.showMessageDialog(this, "请先选择要复制的数据！");
            return;
        }

        StringBuilder clipboardData = new StringBuilder();

        for (int row : selectedRows) {
            for (int col : selectedColumns) {
                clipboardData.append(table.getValueAt(row, col)).append("\t");
            }
            clipboardData.append("\n");
        }

        StringSelection stringSelection = new StringSelection(clipboardData.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        JOptionPane.showMessageDialog(this, "数据已复制到剪贴板！");
    }

    // 初始化左侧数据表浏览窗口
    private void initializeDataList() {
        // 初始化 JList
        dataList = new JList<>(listModel);
        dataList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataList.setBorder(BorderFactory.createTitledBorder("数据表浏览"));

        // 为列表添加选择事件，选择不同的数据表时更新主表格
        dataList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedTable = dataList.getSelectedValue();
                if (selectedTable != null && operater != null) {
                    loadTableData(selectedTable);
                }
            }
        });
    }

    /**
     * 加载指定表的数据到主表格中，使用分页查询。
     *
     * @param tableName 要加载的数据表名称。
     */
    private void loadTableData(String tableName) {
        if (operater == null || currentDBPath == null) {
            JOptionPane.showMessageDialog(this, "数据库未连接！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentTable = tableName;
        currentPage = 1; // 重置为第一页

        // 获取总记录数
        totalRecords = operater.getTotalRecords(currentTable);
        totalPages = (int) Math.ceil((double) totalRecords / pageSize);
        if (totalPages == 0) totalPages = 1;

        // 加载第一页的数据
        updateTable();
        updatePageLabel();

        // 重置搜索参数
        currentSearchFields = null;
        currentSearchValues = null;
    }

    /**
     * 加载当前页的数据并更新表格。
     */
    private void updateTable() {
        if (currentTable == null) return;

        Vector<String> columnNames;
        Vector<Vector<Object>> dataRows;

        try {
            if (currentSearchFields != null && currentSearchValues != null) {
                // 在搜索模式下，从搜索结果中获取数据
                SelectResult searchResult = operater.select(currentTable, currentSearchFields, currentSearchValues, currentPage, pageSize);
                columnNames = searchResult.getColumnNames();
                dataRows = searchResult.getDataRows();
            } else {
                // 普通模式，从数据库操作对象中获取数据
                columnNames = operater.getTableColumnNames(currentTable);
                dataRows = operater.getTablePageData(currentTable, pageSize, currentPage);
            }

            updateTableWithDatabaseData(columnNames, dataRows);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 更新分页标签和按钮状态。
     */
    private void updatePageLabel() {
        pageLabel.setText(currentPage + " / " + totalPages);
        prevButton.setEnabled(currentPage > 1);
        nextButton.setEnabled(currentPage < totalPages);
    }

    /**
     * 搜索对话框类，用于获取用户的搜索字段和关键字。
     */
    private static class SearchDialog extends JDialog {
        private boolean confirmed = false;
        private JComboBox<String> columnComboBox;
        private JTextField keywordField;

        public SearchDialog(Frame owner, String title, Vector<String> columnNames) {
            super(owner, title, true);
            initialize(columnNames);
        }

        private void initialize(Vector<String> columnNames) {
            setLayout(new BorderLayout());

            JPanel inputPanel = new JPanel(new GridLayout(2, 2, 10, 10));
            inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            inputPanel.add(new JLabel("选择字段:"));
            columnComboBox = new JComboBox<>(columnNames);
            inputPanel.add(columnComboBox);

            inputPanel.add(new JLabel("关键字:"));
            keywordField = new JTextField();
            inputPanel.add(keywordField);

            add(inputPanel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton okButton = new JButton("确定");
            JButton cancelButton = new JButton("取消");
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);

            okButton.addActionListener(e -> {
                confirmed = true;
                setVisible(false);
            });

            cancelButton.addActionListener(e -> {
                confirmed = false;
                setVisible(false);
            });

            getRootPane().setDefaultButton(okButton);
            pack();
            setLocationRelativeTo(getOwner());
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public String getSelectedColumn() {
            return (String) columnComboBox.getSelectedItem();
        }

        public String getKeyword() {
            return keywordField.getText();
        }
    }

    /**
     * 进度对话框类，用于显示导入进度。
     */
    private static class ImportProgressDialog extends JDialog {
        private JProgressBar progressBar;
        private JLabel statusLabel;

        public ImportProgressDialog(Frame owner, String title, String message) {
            super(owner, title, true);
            initialize(message);
        }

        private void initialize(String message) {
            setLayout(new BorderLayout());

            statusLabel = new JLabel(message);
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(statusLabel, BorderLayout.NORTH);

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            add(progressBar, BorderLayout.CENTER);

            setSize(400, 100);
            setLocationRelativeTo(getOwner());
        }

        public void updateProgress(int processed, int total) {
            if (total == 0) {
                progressBar.setIndeterminate(true);
            } else {
                int percent = (int) ((double) processed / total * 100);
                progressBar.setIndeterminate(false);
                progressBar.setValue(percent);
                progressBar.setString(processed + " / " + total);
            }
        }
    }

    /**
     * 导出选项枚举。
     */
    private enum ExportOption {
        CURRENT_PAGE,
        PAGE_RANGE,
        ALL_DATA
    }

    /**
     * 导出选项对话框类，用于获取用户的导出选择。
     */
    private static class ExportOptionsDialog extends JDialog {
        private boolean confirmed = false;
        private ExportOption selectedOption = ExportOption.CURRENT_PAGE;
        private JTextField startPageField;
        private JTextField endPageField;
        private final ButtonGroup optionGroup = new ButtonGroup();

        public ExportOptionsDialog(Frame owner, String title) {
            super(owner, title, true);
            initialize();
        }

        private void initialize() {
            setLayout(new BorderLayout());

            JPanel optionsPanel = new JPanel(new GridLayout(3, 1));
            optionsPanel.setBorder(BorderFactory.createTitledBorder("选择导出选项"));

            JRadioButton currentPageRadio = new JRadioButton("当前页");
            currentPageRadio.setSelected(true);
            JRadioButton pageRangeRadio = new JRadioButton("指定页范围");
            JRadioButton allDataRadio = new JRadioButton("所有数据");

            optionGroup.add(currentPageRadio);
            optionGroup.add(pageRangeRadio);
            optionGroup.add(allDataRadio);

            optionsPanel.add(currentPageRadio);
            optionsPanel.add(pageRangeRadio);
            optionsPanel.add(allDataRadio);

            add(optionsPanel, BorderLayout.NORTH);

            JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            rangePanel.add(new JLabel("起始页:"));
            startPageField = new JTextField(5);
            startPageField.setEnabled(false);
            rangePanel.add(startPageField);
            rangePanel.add(new JLabel("结束页:"));
            endPageField = new JTextField(5);
            endPageField.setEnabled(false);
            rangePanel.add(endPageField);

            add(rangePanel, BorderLayout.CENTER);

            // 监听单选按钮的状态变化，启用或禁用页码输入框
            currentPageRadio.addActionListener(e -> toggleRangeFields(false));
            pageRangeRadio.addActionListener(e -> toggleRangeFields(true));
            allDataRadio.addActionListener(e -> toggleRangeFields(false));

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton okButton = new JButton("确定");
            JButton cancelButton = new JButton("取消");
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);

            okButton.addActionListener(e -> {
                confirmed = true;
                if (pageRangeRadio.isSelected()) {
                    try {
                        int start = Integer.parseInt(startPageField.getText().trim());
                        int end = Integer.parseInt(endPageField.getText().trim());
                        if (start < 1 || end < start) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "起始页和结束页必须为有效的数字，并且结束页不小于起始页！", "输入错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                setVisible(false);
            });

            cancelButton.addActionListener(e -> {
                confirmed = false;
                setVisible(false);
            });

            getRootPane().setDefaultButton(okButton);
            pack();
            setLocationRelativeTo(getOwner());
        }

        private void toggleRangeFields(boolean enabled) {
            startPageField.setEnabled(enabled);
            endPageField.setEnabled(enabled);
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public ExportOption getSelectedOption() {
            return selectedOption;
        }

        public int getStartPage() {
            try {
                return Integer.parseInt(startPageField.getText().trim());
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        public int getEndPage() {
            try {
                return Integer.parseInt(endPageField.getText().trim());
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }

    /**
     * 导出数据的方法。
     */
    private void exportData() {
        if (currentTable == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个数据表！", "操作错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 创建导出选项对话框
        ExportOptionsDialog exportDialog = new ExportOptionsDialog(this, "导出数据");
        exportDialog.setVisible(true);

        if (!exportDialog.isConfirmed()) {
            return; // 用户取消导出
        }

        ExportOption selectedOption = exportDialog.getSelectedOption();
        int startPage = exportDialog.getStartPage();
        int endPage = exportDialog.getEndPage();

        // 选择导出文件
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择导出文件");
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files (*.csv)", "csv");
        fileChooser.setFileFilter(filter);
        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File exportFile = fileChooser.getSelectedFile();
        String exportPath = exportFile.getAbsolutePath();
        if (!exportPath.toLowerCase().endsWith(".csv")) {
            exportPath += ".csv";
            exportFile = new File(exportPath);
        }

        // 确认是否覆盖已有文件
        if (exportFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this, "文件已存在，是否覆盖？", "确认覆盖", JOptionPane.YES_NO_OPTION);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // 创建 CSVExporter 实例
        CSVExporter exporter = new CSVExporter();

        // 创建进度对话框
        ExportProgressDialog progressDialog = new ExportProgressDialog(this, "导出中...", "正在导出数据，请稍候...");

        String finalExportPath = exportPath;
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    Vector<String> columnNames = operater.getTableColumnNames(currentTable);
                    Vector<Vector<Object>> allData = new Vector<>();

                    switch (selectedOption) {
                        case CURRENT_PAGE:
                            SelectResult currentPageResult = operater.select(currentTable, currentSearchFields, currentSearchValues, currentPage, pageSize);
                            allData.addAll(currentPageResult.getDataRows());
                            progressDialog.updateProgress(1, 1);
                            break;
                        case PAGE_RANGE:
                            if (startPage < 1 || endPage > totalPages || startPage > endPage) {
                                throw new IllegalArgumentException("页码范围不合法！");
                            }
                            int totalExportPages = endPage - startPage + 1;
                            for (int page = startPage; page <= endPage; page++) {
                                SelectResult pageResult = operater.select(currentTable, currentSearchFields, currentSearchValues, page, pageSize);
                                allData.addAll(pageResult.getDataRows());
                                progressDialog.updateProgress(page - startPage + 1, totalExportPages);
                            }
                            break;
                        case ALL_DATA:
                            // 计算总页数
                            int totalExportAllPages = totalPages;
                            for (int page = 1; page <= totalExportAllPages; page++) {
                                SelectResult pageResult = operater.select(currentTable, currentSearchFields, currentSearchValues, page, pageSize);
                                allData.addAll(pageResult.getDataRows());
                                progressDialog.updateProgress(page, totalExportAllPages);
                            }
                            break;
                    }

                    // 导出到 CSV
                    exporter.exportToCSV(finalExportPath, columnNames, allData);

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(MainView.this, "导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE));
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(MainView.this, "数据导出成功到: " + finalExportPath, "导出成功", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    /**
     * 进度对话框类，用于显示导出进度。
     */
    private static class ExportProgressDialog extends JDialog {
        private JProgressBar progressBar;
        private JLabel statusLabel;

        public ExportProgressDialog(Frame owner, String title, String message) {
            super(owner, title, true);
            initialize(message);
        }

        private void initialize(String message) {
            setLayout(new BorderLayout());

            statusLabel = new JLabel(message);
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            add(statusLabel, BorderLayout.NORTH);

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            add(progressBar, BorderLayout.CENTER);

            setSize(400, 100);
            setLocationRelativeTo(getOwner());
        }

        public void updateProgress(int processed, int total) {
            if (total == 0) {
                progressBar.setIndeterminate(true);
            } else {
                int percent = (int) ((double) processed / total * 100);
                progressBar.setIndeterminate(false);
                progressBar.setValue(percent);
                progressBar.setString(processed + " / " + total);
            }
        }
    }

    /**
     * 高阶搜索对话框类，用于获取用户的搜索字段、关键字和搜索模式。
     */
    private static class UltraFindDialog extends JDialog {
        private boolean confirmed = false;
        private JComboBox<String> columnComboBox;
        private JTextField keywordField;
        private JComboBox<String> searchModeComboBox;

        public UltraFindDialog(Frame owner, String title, Vector<String> columnNames) {
            super(owner, title, true);
            initialize(columnNames);
        }

        private void initialize(Vector<String> columnNames) {
            setLayout(new BorderLayout());

            JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 10));
            inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            inputPanel.add(new JLabel("选择字段:"));
            columnComboBox = new JComboBox<>(columnNames);
            inputPanel.add(columnComboBox);

            inputPanel.add(new JLabel("关键字:"));
            keywordField = new JTextField();
            inputPanel.add(keywordField);

            inputPanel.add(new JLabel("搜索模式:"));
            searchModeComboBox = new JComboBox<>(new String[]{"标准搜索", "高阶搜索"});
            inputPanel.add(searchModeComboBox);

            add(inputPanel, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton okButton = new JButton("确定");
            JButton cancelButton = new JButton("取消");
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);

            okButton.addActionListener(e -> {
                confirmed = true;
                setVisible(false);
            });

            cancelButton.addActionListener(e -> {
                confirmed = false;
                setVisible(false);
            });

            getRootPane().setDefaultButton(okButton);
            pack();
            setLocationRelativeTo(getOwner());
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public String getSelectedColumn() {
            return (String) columnComboBox.getSelectedItem();
        }

        public String getKeyword() {
            return keywordField.getText();
        }

        public String getSearchMode() {
            return (String) searchModeComboBox.getSelectedItem();
        }
    }

    /**
     * 执行高阶搜索的方法。
     */
    private void performUltraFind() throws SQLException {
        if (currentTable == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个数据表！", "操作错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 获取当前表的所有列名
        Vector<String> columnNames = operater.getTableColumnNames(currentTable);
        if (columnNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前表没有列可供搜索！", "信息", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 弹出高阶搜索对话框
        UltraFindDialog ultraFindDialog = new UltraFindDialog(this, "高阶查找", columnNames);
        ultraFindDialog.setVisible(true);

        if (ultraFindDialog.isConfirmed()) {
            String selectedColumn = ultraFindDialog.getSelectedColumn();
            String keyword = ultraFindDialog.getKeyword();
            String searchMode = ultraFindDialog.getSearchMode();

            if (selectedColumn != null && !selectedColumn.trim().isEmpty() &&
                    keyword != null && !keyword.trim().isEmpty()) {
                String[] fields = {selectedColumn.trim()};
                String[] values = {"%" + keyword.trim() + "%"}; // 使用 LIKE 进行模糊匹配
                boolean isAdvancedSearch = "高阶搜索".equals(searchMode);

                if (isAdvancedSearch) {
                    // 执行高阶搜索
                    executeAdvancedSearch(fields, values);
                } else {
                    // 执行标准搜索
                    performSearch(fields, values);
                }
            } else {
                JOptionPane.showMessageDialog(this, "字段和关键字不能为空！", "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 执行高阶搜索，计算相似度并筛选Top 100
     */
    private void executeAdvancedSearch(String[] fieldNames, String[] searchValues) {
        try {
            // 执行标准搜索获取初步结果，限制为1000条
            SelectResult searchResult = operater.selectTopN(currentTable, fieldNames, searchValues, 1000);

            Vector<String> columnNames = searchResult.getColumnNames();
            Vector<Vector<Object>> dataRows = searchResult.getDataRows();

            if (columnNames.isEmpty() || dataRows.isEmpty()) {
                JOptionPane.showMessageDialog(this, "没有找到符合条件的记录！", "信息", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 假设只搜索第一个字段
            String searchField = fieldNames[0];
            int searchFieldIndex = columnNames.indexOf(searchField);
            if (searchFieldIndex == -1) {
                JOptionPane.showMessageDialog(this, "搜索字段不存在！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String searchKeyword = searchValues[0].replace("%", "").toLowerCase();

            // 计算相似度并创建 SimilarRecord 列表
            List<SimilarRecord> similarRecords = dataRows.parallelStream()
                    .map(row -> {
                        String fieldValue = row.get(searchFieldIndex).toString();
                        double similarity = calculateSimilarity(searchKeyword, fieldValue);
                        return new SimilarRecord(similarity, row);
                    })
                    .collect(Collectors.toList());

            // 对记录按相似度降序排序
            similarRecords.sort(Comparator.comparingDouble(SimilarRecord::getSimilarity).reversed());

            // 取Top 100
            List<SimilarRecord> topSimilarRecords = similarRecords.stream().limit(100).collect(Collectors.toList());

            // 准备结果数据，添加相似度分数
            Vector<Vector<Object>> topDataRows = new Vector<>();
            for (SimilarRecord record : topSimilarRecords) {
                Vector<Object> newRow = new Vector<>();
                newRow.add(String.format("%.2f", record.getSimilarity())); // 保留两位小数
                newRow.addAll(record.getRow());
                topDataRows.add(newRow);
            }

            // 更新表格显示，添加“匹配度”列
            updateTableWithDatabaseData(columnNames, topDataRows, true);

            // 更新分页信息
            currentPage = 1;
            totalRecords = topDataRows.size();
            totalPages = 1;
            updatePageLabel();

            // 设置当前搜索参数
            currentSearchFields = fieldNames;
            currentSearchValues = searchValues;

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "高阶查找失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 内部类用于存储记录及其相似度
     */
    private static class SimilarRecord {
        private double similarity;
        private Vector<Object> row;

        public SimilarRecord(double similarity, Vector<Object> row) {
            this.similarity = similarity;
            this.row = row;
        }

        public double getSimilarity() {
            return similarity;
        }

        public Vector<Object> getRow() {
            return row;
        }
    }

    /**
     * 计算两个字符串的相似度
     *
     * @param query  用户输入的搜索关键词
     * @param target 数据库中的字段值
     * @return 相似度分数（0.0 - 1.0）
     */
    private double calculateSimilarity(String query, String target) {
        if (query == null || target == null) {
            return 0.0;
        }

        // 统一小写，去除前后空白
        query = query.toLowerCase().trim();
        target = target.toLowerCase().trim();

        // 计算Levenshtein距离
        int levDistance = levenshteinDistance.apply(query, target);
        int maxLength = Math.max(query.length(), target.length());
        double levSimilarity = maxLength == 0 ? 1.0 : 1.0 - ((double) levDistance / maxLength);

        // 计算Jaro-Winkler相似度
        double jaroSimilarity = jaroWinklerDistance.apply(query, target);

        // 综合相似度（权重可根据需要调整）
        double combinedSimilarity = (levSimilarity * 0.5) + (jaroSimilarity * 0.5);

        return combinedSimilarity;
    }

    /**
     * 更新表格数据的方法
     *
     * @param columnNames      列名
     * @param dataRows         数据行
     * @param isAdvancedSearch 是否为高阶搜索
     */
    private void updateTableWithDatabaseData(Vector<String> columnNames, Vector<Vector<Object>> dataRows, boolean isAdvancedSearch) {
        // 清空现有表格
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);

        // 如果是高阶搜索，添加“匹配度”列
        if (isAdvancedSearch) {
            tableModel.addColumn("匹配度");
        }

        // 添加数据库表的列名
        for (String colName : columnNames) {
            tableModel.addColumn(colName);
        }

        // 添加数据行，限制为Top 100
        for (Vector<Object> row : dataRows) {
            tableModel.addRow(row);
        }
    }

    private void updateTableWithDatabaseData(Vector<String> columnNames, Vector<Vector<Object>> dataRows) {
        updateTableWithDatabaseData(columnNames, dataRows, false);  // 默认值为false
    }
}
