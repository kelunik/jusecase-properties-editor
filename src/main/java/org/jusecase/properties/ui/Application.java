package org.jusecase.properties.ui;

import net.miginfocom.swing.MigLayout;
import org.jusecase.properties.BusinessLogic;
import org.jusecase.properties.entities.Key;
import org.jusecase.properties.entities.UndoableRequest;
import org.jusecase.properties.plugins.Plugin;
import org.jusecase.properties.usecases.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class Application {

    private final BusinessLogic businessLogic = createBusinessLogic();

    private JFrame frame;
    private JList<Key> keyList;
    private final KeyListModel keyListModel = new KeyListModel();
    private JTextField searchField;
    private JCheckBox caseSensitiveBox;
    private JCheckBox regexBox;
    private JCheckBox changesBox;
    private JPanel keyPanel;
    private TranslationsPanel translationsPanel;
    private ApplicationMenuBar menuBar;
    private LookAndFeel lookAndFeel;

    protected static String applicationName = "Properties Editor";
    protected static Class<? extends Application> applicationClass = Application.class;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());

        macSetup(applicationName);

        SwingUtilities.invokeLater(() -> {
            try {
                applicationClass.getDeclaredConstructor().newInstance().start();
            }
            catch ( Exception e ) {
                throw new RuntimeException(e);
            }
        });
    }

    protected BusinessLogic createBusinessLogic() {
        return new BusinessLogic();
    }

    private static void macSetup(String appName) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("mac")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", appName);
            System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
        }
    }

    public <Request> void execute(Request request) {
        execute(request, null);
    }

    public <Request, Response> void execute(Request request, Consumer<Response> responseConsumer) {
        Response response = businessLogic.execute(request);
        if (responseConsumer != null && response != null) {
            responseConsumer.accept(response);
        }

        if (request instanceof UndoableRequest) {
            menuBar.updateUndoAndRedoItems();
        }
    }

    public void importProperties( Plugin plugin ) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("Choose file to import");
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Import.Request request = new Import.Request();
            List<Path> files = new ArrayList<>();
            for ( File file : fileChooser.getSelectedFiles() ) {
                files.add(file.toPath());
            }
            request.files = files;
            request.pluginId = plugin.getPluginId();
            execute(request, (Consumer<Import.Response>) response -> {
                JOptionPane.showMessageDialog(null, "Added " + response.amountAdded + " new values and updated " + response.amountChanged + " existing values.", "Import was successful", JOptionPane.PLAIN_MESSAGE);
                refreshSearch();
            });
        }
    }

    public void exportProperties(Plugin plugin) {
        Export.Request request = new Export.Request();
        request.keys = keyList.getSelectedValuesList();
        request.pluginId = plugin.getPluginId();
        execute(request);
    }

    public void loadProperties(File file) {
        LoadBundle.Request request = new LoadBundle.Request();
        request.propertiesFile = file.toPath();
        execute(request, this::onLoadPropertiesComplete);
    }

    public void save() {
        execute(new SaveBundle.Request());
        searchAfterSave();
    }

    public void saveAll() {
        SaveBundle.Request request = new SaveBundle.Request();
        request.saveAll = true;
        execute(request);
        searchAfterSave();
    }

    private Search.Request createSearchRequest() {
        Search.Request request = new Search.Request();
        request.query = searchField.getText();
        request.regex = regexBox.isSelected();
        request.caseSensitive = caseSensitiveBox.isSelected();
        request.changes = changesBox.isSelected();
        return request;
    }

    public void search(String query) {
        Search.Request request = createSearchRequest();
        request.query = query;
        search(request, response -> {
            updateKeyList(response.keys);

            if (response.keys.isEmpty()) {
                translationsPanel.reset();
            } else {
                if (getSelectedKey() == null) {
                    keyList.setSelectedIndex(0);
                }
                translationsPanel.setSearchRequest(request);
            }
        });
    }

    private void searchAfterSave() {
        if (changesBox.isSelected()) {
            refreshSearch();
        }
    }

    private void searchAfterChange(Consumer<Search.Response> responseConsumer) {
        Search.Request searchRequest = createSearchRequest();
        searchRequest.reloadChanges = true;
        search(searchRequest, responseConsumer);
    }

    private void search(Search.Request request, Consumer<Search.Response> responseConsumer) {
        try {
            resetRegexError();
            execute(request, responseConsumer);
        } catch ( PatternSyntaxException e ) {
            setRegexError(e);
        }
    }

    private void setRegexError( PatternSyntaxException e ) {
        regexBox.setText(e.getMessage());
        regexBox.setForeground(Color.red);
    }

    private void resetRegexError() {
        regexBox.setText(getRegexBoxTitle());
        regexBox.setForeground(keyList.getForeground());
    }

    private void updateKeyList( List<Key> keys) {
        Key selectedKey = getSelectedKeyEntity();

        keyListModel.setKeys(keys);

        int index = keys.indexOf(selectedKey);
        if (index >= 0) {
            keyList.setSelectedIndex(index);
            keyList.ensureIndexIsVisible(index);
        } else {
            keyList.clearSelection();
            keyList.ensureIndexIsVisible(0);
        }
    }

    private void onLoadPropertiesComplete(LoadBundle.Response response) {
        if (response.keys != null) {
            translationsPanel.setFileNames(response.fileNames);

            updateKeyList(response.keys);
            if (!response.keys.isEmpty()) {
                keyList.setSelectedIndex(0);
                updateTranslationPanel(response.keys.get(0).getKey());
            }
        }
    }

    protected void start() {
        initLookAndFeel();

        initFrame();
        initMenuBar();
        initPanel();

        execute(new Initialize.Request(), this::onLoadPropertiesComplete);
    }

    protected void initLookAndFeel() {
        execute(new LoadLookAndFeel.Request(), this::applyLookAndFeel);
    }

    public LookAndFeel getLookAndFeel() {
        return lookAndFeel;
    }

    private void initPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 0"));

        initKeyPanel();
        initTranslationsPanel();

        JScrollPane translationsPanelScrollPane = new JScrollPane(translationsPanel);
        translationsPanelScrollPane.setBorder(null);
        translationsPanelScrollPane.getVerticalScrollBar().setUnitIncrement(8);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, keyPanel, translationsPanelScrollPane);
        splitPane.setDividerLocation(0.3);
        panel.add(splitPane, "push,grow");

        frame.add(panel);
    }

    private void initKeyPanel() {
        keyPanel = new JPanel(new MigLayout("insets 2,fill"));
        initSearchField();
        initKeyList();
    }

    private void initKeyList() {
        keyList = new JList<>(keyListModel);
        keyList.addListSelectionListener(e -> updateTranslationPanel(getSelectedKey()));
        keyList.setComponentPopupMenu(new KeyListMenu(this));
        keyList.setCellRenderer(new KeyListCellRenderer(this));

        JScrollPane scrollPane = new JScrollPane(keyList);
        keyPanel.add(scrollPane, "wrap,push,grow,w 250::");
    }

    public void onNewKeyAdded(String key) {
        searchAfterChange(response -> {
            Key keyEntity = new Key(key);
            if (response.keys.contains(keyEntity)) {
                keyListModel.setKeys(response.keys);
            } else {
                resetSearch();
            }
            keyList.clearSelection();
            keyList.setSelectedValue(keyEntity, true);
        });
    }

    private void resetSearch() {
        triggerSearch("");
    }

    private void triggerSearch(String query) {
        searchField.setText(query);
        search(query);
    }

    public void onKeyRenamed() {
        refreshSearch();
    }

    public void refreshSearch() {
        searchAfterChange(response -> {
            int previousSelectedIndex = keyList.getSelectedIndex();
            keyListModel.setKeys(response.keys);
            keyList.setSelectedIndex(Math.max(0, previousSelectedIndex));
        });
    }

    private void updateTranslationPanel(String key) {
        if (key != null) {
            GetProperties.Request request = new GetProperties.Request();
            request.key = key;
            execute(request, (Consumer<GetProperties.Response>) response -> {
                translationsPanel.setProperties(response.properties);
            });
        } else {
            translationsPanel.reset();
        }
    }

    private void initSearchField() {
        JPanel searchPanel = new JPanel(new MigLayout("insets 0,fill"));

        searchField = new JTextField();
        TextUtils.enableDefaultShortcuts(searchField);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                search(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search(searchField.getText());
            }
        });
        searchPanel.add(searchField, "pushx,growx,wmax 100%");

        JButton searchHistoryButton = new JButton(UIManager.getIcon("FileView.computerIcon"));
        searchHistoryButton.addActionListener(e -> {
            GetSearchHistory.Request request = new GetSearchHistory.Request();
            request.currentQuery = searchField.getText();
            execute(request, (GetSearchHistory.Response r) -> {
                JPopupMenu popMenu = new JPopupMenu();
                for (String query : r.queries) {
                    JMenuItem menuItem = new JMenuItem(query);
                    menuItem.addActionListener(menuItemEvent -> {
                        triggerSearch(query);
                    });
                    popMenu.add(menuItem);
                }
                popMenu.show(searchHistoryButton, searchField.getX(), searchField.getY() + searchField.getHeight());
            });
        });
        searchPanel.add(searchHistoryButton, "align right");

        keyPanel.add(searchPanel, "growx, wrap");

        caseSensitiveBox = new JCheckBox("Case sensitive", false);
        caseSensitiveBox.addItemListener(e -> search(searchField.getText()));
        keyPanel.add(caseSensitiveBox, "wrap,growx");

        regexBox = new JCheckBox(getRegexBoxTitle(), false);
        regexBox.addItemListener(e -> {
            resetRegexError();
            search(searchField.getText());
        });
        keyPanel.add(regexBox, "wrap,growx");

        changesBox = new JCheckBox("Changed Properties", false);
        changesBox.addItemListener(e -> search(searchField.getText()));
        keyPanel.add(changesBox, "wrap,growx");
    }

    private String getRegexBoxTitle() {
        return "Regex (exact match)";
    }

    private void initMenuBar() {
        menuBar = new ApplicationMenuBar(this);
        frame.setJMenuBar(menuBar);
    }

    private void initFrame() {
        frame = new JFrame(applicationName);

        Dimension screenSize = getScreenSize();
        screenSize.width = (int)(0.4 * screenSize.width);
        screenSize.height = (int)(0.4 * screenSize.height);
        frame.setMinimumSize(screenSize);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        initIcons(frame);
        frame.setVisible(true);
        frame.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                execute(new CheckModifications.Request(), (CheckModifications.Response response) -> {
                    switch (response) {
                        case NoActionRequired:
                            // This is the best case ;-)
                            break;
                        case ReloadSilently:
                            reloadSilently();
                            break;
                        case AskUser:
                            askUserToReload();
                            break;
                    }
                });
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing( WindowEvent e ) {
                execute(new IsAllowedToQuit.Request(), (IsAllowedToQuit.Response response) -> {
                    if (response.askUser) {
                        int result = JOptionPane.showConfirmDialog (null, "Do you want to save your unsaved changes?", "Warning", JOptionPane.YES_NO_CANCEL_OPTION);
                        if (result == JOptionPane.CANCEL_OPTION) {
                            return;
                        }

                        if (result == JOptionPane.YES_OPTION) {
                            save();
                        }
                    }
                    System.exit(0);
                });
            }
        });
    }

    protected Dimension getScreenSize() {
        GraphicsDevice defaultScreenDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode displayMode = defaultScreenDevice.getDisplayMode();
        return new Dimension(displayMode.getWidth(), displayMode.getHeight());
    }

    @SuppressWarnings("unused")
    protected void initIcons(JFrame frame) {
    }

    private void reloadSilently() {
        execute(new ReloadBundle.Request(), this::onLoadPropertiesComplete);
    }

    private void askUserToReload() {
        int result = JOptionPane.showConfirmDialog (null, "External changes detected. Do you want to reload and lose your unsaved changes?", "Warning", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            reloadSilently();
        } else {
            execute(new IgnoreModifications.Request());
        }
    }

    private void initTranslationsPanel() {
        translationsPanel = new TranslationsPanel(this);
    }

    public JFrame getFrame() {
        return frame;
    }

    public String getSelectedKey() {
        Key selectedValue = getSelectedKeyEntity();
        return selectedValue == null ? null : selectedValue.getKey();
    }

    public Key getSelectedKeyEntity() {
        if (keyList.getSelectedIndex() < keyListModel.getSize()) {
            return keyList.getSelectedValue();
        }
        return null;
    }

    public List<String> getSelectedValues() {
        return keyList.getSelectedValuesList().stream().map(Key::getKey).collect(Collectors.toList());
    }

    public void undo() {
        execute(new Undo.Request(), (Undo.Response response) -> {
            handleUndoOrRedoResponse(response.undoRequest);
        });
    }

    public void redo() {
        execute(new Redo.Request(), (Redo.Response response) -> {
            handleUndoOrRedoResponse(response.redoRequest);
        });
    }

    private void handleUndoOrRedoResponse(Object request) {
        if (request instanceof EditValue.Request) {
            EditValue.Request editValue = (EditValue.Request)request;
            if (editValue.property.key.equals(getSelectedKey())) {
                updateTranslationPanel(editValue.property.key);
            }
        }

        if (request instanceof NewKey.Request) {
            NewKey.Request newKey = (NewKey.Request) request;
            if (newKey.undo) {
                onKeyDeleted();
            } else {
                onNewKeyAdded(newKey.key);
            }
        }

        if (request instanceof DeleteKey.Request) {
            DeleteKey.Request deleteKey = (DeleteKey.Request) request;
            if (deleteKey.undo) {
                onNewKeyAdded(deleteKey.key);
            } else {
                onKeyDeleted();
            }
        }

        if (request instanceof DuplicateKey.Request) {
            DuplicateKey.Request duplicateKey = (DuplicateKey.Request) request;
            if (duplicateKey.undo) {
                onKeyDeleted();
            } else {
                onNewKeyAdded(duplicateKey.newKey);
            }
        }

        if (request instanceof DuplicateKeys.Request) {
            DuplicateKeys.Request duplicateKey = (DuplicateKeys.Request) request;
            if (duplicateKey.undo) {
                onKeyDeleted();
            } else {
                duplicateKey.newKeys.forEach(this::onNewKeyAdded);
            }
        }

        if (request instanceof RenameKey.Request || request instanceof RenameKeys.Request) {
            onKeyRenamed();
        }

        if (request instanceof Import.Request) {
            refreshSearch();
        }
    }

    public void onKeyDeleted() {
        refreshSearch();
    }

    public void changeLookAndFeel(LookAndFeel lookAndFeel) {
        SaveLookAndFeel.Request request = new SaveLookAndFeel.Request();
        request.lookAndFeel = lookAndFeel;
        execute(request);

        applyLookAndFeel(lookAndFeel);

        SwingUtilities.updateComponentTreeUI(frame);
    }

    protected void applyLookAndFeel(LookAndFeel lookAndFeel) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            this.lookAndFeel = lookAndFeel;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load look and feel " + lookAndFeel);
        }
    }
}
