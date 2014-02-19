/******************************************************************************* 
 * MyOfflineTrades - Trading when market is closed or at any time ! 
 *******************************************************************************
 * Description:
 * This strategy allows manual trades on Historical Tester of JForex.
 * Trades can be done at market price, with conditional and limit orders.
 * Also we can set Take Profit and Stop Loss at order creation and change them
 * at any time of the order existence.
 * 
 * Developed by JLongo (profile name under Dukascopy Community)
 * Version: 0.1.1 - 20121009-21:00
 ******************************************************************************/
package JForex.myStrategies;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Main Class of the strategy
 */
@RequiresFullAccess // Some functions may need this
public class myOfflineTrades implements IStrategy {
    
    // <editor-fold defaultstate="" desc="Main class variables">
    // base objects creation
    private IContext myContext = null;
    private IEngine myEngine = null;
    private IConsole myConsole = null;
    private IHistory myHistory = null;
    private IAccount myAccount = null;
    
    // configurable variables
    @Configurable("Select instrument:")
    public Instrument myInstrument = Instrument.EURUSD;
    
    // other variables
    private OfflineForm myForm = null;
    public double myPipValue;
    public int myPipScale;
    // </editor-fold>
    
    /**
     * onStart Function
     * 
     * @param context
     * @throws JFException 
     */
    @Override
    public void onStart(IContext context) throws JFException {
        //objects and variable initialization
        myContext = context;
        myEngine = context.getEngine();
        myConsole = myContext.getConsole();
        myHistory = myContext.getHistory();
        myAccount = myContext.getAccount();
        myPipValue = myInstrument.getPipValue();
        myPipScale = myInstrument.getPipScale();
        myForm = new OfflineForm();
        // avoid to run outside of Historical Tester
        if (myEngine.getType() != IEngine.Type.TEST){
            JOptionPane.showMessageDialog(null, "This strategy only runs on Historical Tester !", "Strategy Alert",JOptionPane.WARNING_MESSAGE);
            myContext.stop();
        }else{
            Set subscribedInstruments = new HashSet();
            subscribedInstruments.add(myInstrument);
            myContext.setSubscribedInstruments(subscribedInstruments);
            myForm.setVisible(true);
        }

    }// end onStart

    /**
     * onTick Function
     * 
     * @param instrument
     * @param tick
     * @throws JFException 
     */
    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (myInstrument == instrument){ // filter 
            myForm.updatePrices(tick);
            // get orders
            List<IOrder> myAllOrders = myEngine.getOrders(); 
            if (myAllOrders.size() > 0){
                // if any order update the values of the table
                for(IOrder order : myAllOrders){
                    if (order == null){
                        myForm.synchronize();
                    }else{
                        myForm.updateTableOrder(order);
                    }
                }
            }
        }
        
    }// end onTick

    /**
     * onBar Function
     * 
     * @param instrument
     * @param period
     * @param askBar
     * @param bidBar
     * @throws JFException 
     */
    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        
    }// end onBar

    /**
     * onMessage Function
     * 
     * @param message
     * @throws JFException 
     */
    @Override
    public void onMessage(IMessage message) throws JFException {
        // remove of the order on the table at order close
        if(message.getType() == IMessage.Type.ORDER_CLOSE_OK){
            myForm.removeTableOrder(message.getOrder().getId());
        }
        // when the server accepted the order fill the stop loss and take profit
        // if we choose them and put the order on the table
        if(message.getType() == IMessage.Type.ORDER_SUBMIT_OK){
            if(!(message.getOrder().getOrderCommand() == OrderCommand.BUY || message.getOrder().getOrderCommand() == OrderCommand.SELL)){
                myForm.setTpSl(message.getOrder());
            }
            myForm.createTableOrder(message.getOrder());
        }
        // same as before but for order fill and only update the order status
        // if not filled on the table
        if (message.getType() == IMessage.Type.ORDER_FILL_OK){
            if(message.getOrder().getOrderCommand() == OrderCommand.BUY || message.getOrder().getOrderCommand() == OrderCommand.SELL){
                myForm.setTpSl(message.getOrder());
            }
            myForm.updateTableOrder(message.getOrder());
        }
        // update of the table at any order change
        if(message.getType() == IMessage.Type.ORDER_CHANGED_OK){
            myForm.updateTableOrder(message.getOrder());
        }
        
    }// end onMessage

    /**
     * Function onAccount
     * 
     * @param account
     * @throws JFException 
     */
    @Override
    public void onAccount(IAccount account) throws JFException {
        
    }// end onAccount

    /**
     * onClose Function
     * 
     * @throws JFException 
     */
    @Override
    public void onStop() throws JFException {
        myForm.setVisible(false);
        myForm.dispose();
    }// end onStop
    
    /**
     * Function print
     * 
     * Print on message area the string passed as argument
     * 
     * @param string 
     */
    public void print(String string){
        myConsole.getOut().println(string);
    }//end print
    
    
    /**
     * Class OfflineForm
     * 
     * Creates the form window where we can setup our trades 
     */
    class OfflineForm extends  JFrame implements ActionListener{
        
        // <editor-fold defaultstate="collapsed" desc="Class variables">
        private  ButtonGroup buttonGroupOptions = new ButtonGroup();
        private  ButtonGroup buttonGroupValuePip = new ButtonGroup();
        private  JLabel fill = new JLabel();
        private  JButton jButtonBuy = new JButton();
        private  JButton jButtonSell = new JButton();
        private  JButton jButtonSynchronize = new JButton();
        private  JLabel jLabel1 = new JLabel();
        private  JLabel jLabel2 = new JLabel();
        private  JLabel jLabel3 = new JLabel();
        private  JLabel jLabelAmount = new JLabel();
        private  JLabel jLabelEntry = new JLabel();
        private  JLabel jLabelPriceAsk = new JLabel();
        private  JLabel jLabelPriceBid = new JLabel();
        private  JLabel jLabelSlipage = new JLabel();
        private  JPanel jPanelButtons = new JPanel();
        private  JPanel jPanelEntry = new JPanel();
        private  JPanel jPanelOptions = new JPanel();
        private  JPanel jPanelPrices = new JPanel();
        private  JPanel jPanelSLandTP = new JPanel();
        private  JPanel jPanelTitles = new JPanel();
        private  JRadioButton jRadioButtonAsk = new JRadioButton();
        private  JRadioButton jRadioButtonBid = new JRadioButton();
        private  JRadioButton jRadioButtonMarket = new JRadioButton();
        private  JRadioButton jRadioButtonLimit = new JRadioButton();
        private  JRadioButton jRadioButtonPip = new JRadioButton();
        private  JRadioButton jRadioButtonValue = new JRadioButton();
        private  JScrollPane jScrollPane1 = new JScrollPane();
        private  JTable jTableOrders = new JTable();
        private  JTextField jTextFieldSL = new JTextField();
        private  JTextField jTextFieldTP = new JTextField();
        private  JTextField jTextFieldAmount = new JTextField();
        private  JTextField jTextFieldEntry = new JTextField();
        private  JTextField jTextFieldSlippage = new JTextField();
        public DefaultTableModel myDefTabMod;
        //order variables
        private double myEntry = Double.NaN;
        private double myAmount = Double.NaN;
        private double myStopLoss = Double.NaN;
        private double myTakeProfit = Double.NaN;
        private double mySlippage = Double.NaN;
        private IOrder myOrder = null;
        private int myOrderCounter = 1;
        // </editor-fold>
        
        /**
        * Offline class contructor
        * 
        * Creates new form OfflineForm
        */
        public OfflineForm() {
            initComponents();

        }// end offlineForm

        /**
        * Function initComponents
        * 
        * Responsible for the form creation and it's components
        */
        @SuppressWarnings("unchecked")
        private void initComponents(){
            
            // <editor-fold defaultstate="collapsed" desc="Components with events">
            jButtonBuy.addActionListener(this);
            jButtonSell.addActionListener(this);
            jRadioButtonAsk.addActionListener(this);
            jRadioButtonBid.addActionListener(this);
            jRadioButtonMarket.addActionListener(this);
            jRadioButtonLimit.addActionListener(this);
            jRadioButtonPip.addActionListener(this);
            jRadioButtonValue.addActionListener(this);
            jButtonSynchronize.addActionListener(this);
            
            // </editor-fold>
            
            setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE);
            setAlwaysOnTop(true);
            setMaximumSize(new  Dimension(300, 1080));
            setMinimumSize(new  Dimension(300, 500));
            setPreferredSize(new  Dimension(300, 500));

            // Ask and bid price panel
            jPanelPrices.setLayout(new  GridLayout(1, 2));

            // Bid Price label and attributes
            jLabelPriceBid.setBackground(new  Color(255, 153, 153));
            jLabelPriceBid.setFont(new  Font("Tahoma", 1, 24)); // NOI18N
            jLabelPriceBid.setHorizontalAlignment( SwingConstants.CENTER);
            jLabelPriceBid.setText("999.999");
            jLabelPriceBid.setOpaque(true);
            jPanelPrices.add(jLabelPriceBid);

            // Ask Price label and attributes
            jLabelPriceAsk.setBackground(new  Color(0, 204, 0));
            jLabelPriceAsk.setFont(new  Font("Tahoma", 1, 24)); // NOI18N
            jLabelPriceAsk.setHorizontalAlignment( SwingConstants.CENTER);
            jLabelPriceAsk.setText("999.999");
            jLabelPriceAsk.setOpaque(true);
            jPanelPrices.add(jLabelPriceAsk);

            // Sell and buy buttons panel
            jPanelButtons.setLayout(new  GridLayout(1, 2));

            // Sell button
            jButtonSell.setFont(new  Font("Tahoma", 1, 24)); // NOI18N
            jButtonSell.setText("Sell");
            jPanelButtons.add(jButtonSell);

            // Buy button
            jButtonBuy.setFont(new  Font("Tahoma", 1, 24)); // NOI18N
            jButtonBuy.setText("Buy");
            jPanelButtons.add(jButtonBuy);

            // Order options panel
            jPanelOptions.setLayout(new  GridLayout(1, 4));

            // Radiobutton group and settings
            // Radiobutton Market and disable entry value if checked 
            buttonGroupOptions.add(jRadioButtonMarket);
            jRadioButtonMarket.setSelected(true);
            jTextFieldEntry.setEnabled(false);
            jRadioButtonMarket.setText("@ Market");
            jRadioButtonMarket.setToolTipText("Entry directly at market value");
            jPanelOptions.add(jRadioButtonMarket);

            // Radiobutton BidPrice
            buttonGroupOptions.add(jRadioButtonBid);
            jRadioButtonBid.setText("Bid Price");
            jRadioButtonBid.setToolTipText("If Sell, Entry <= bid price; if Buy, entry >= bid price ");
            jPanelOptions.add(jRadioButtonBid);

            // RadioButton AskPrice
            buttonGroupOptions.add(jRadioButtonAsk);
            jRadioButtonAsk.setText("Ask Price");
            jRadioButtonAsk.setToolTipText("If Sell, Entry <= ask price; if Buy, entry >= ask price ");
            jPanelOptions.add(jRadioButtonAsk);
            
            // RadioButton Limit
            buttonGroupOptions.add(jRadioButtonLimit);
            jRadioButtonLimit.setText("Limit O.");
            jRadioButtonLimit.setToolTipText("If Sell, Entry >= bid price; if Buy, entry <= ask price ");
            jPanelOptions.add(jRadioButtonLimit);

            // order values area panel
            jPanelEntry.setLayout(new  GridLayout(1, 0));

            // order entry for conditional orders
            jLabelEntry.setHorizontalAlignment( SwingConstants.RIGHT);
            jLabelEntry.setText("Entry  :");
            jPanelEntry.add(jLabelEntry);
            // Entry value and filter keyboard to allow only numbers and dot
            jTextFieldEntry.setHorizontalAlignment( JTextField.RIGHT);
            jTextFieldEntry.setToolTipText("");
            jTextFieldEntry.setText(null);
            jPanelEntry.add(jTextFieldEntry);
            jTextFieldEntry.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    
                    if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE) ||
                        (c == '.'))) {
                        e.consume();
                    }
                    if((c == '.') && jTextFieldEntry.getText().contains(".")){
                        e.consume();
                    }
                }
            });

            // Amount value
            jLabelAmount.setHorizontalAlignment( SwingConstants.RIGHT);
            jLabelAmount.setText("Amount  :");
            jPanelEntry.add(jLabelAmount);
            // Amount value and filter keyboard to allow only numbers and dot
            jTextFieldAmount.setHorizontalAlignment( JTextField.RIGHT);
            jPanelEntry.add(jTextFieldAmount);
            jTextFieldAmount.setToolTipText("Amount in millions (can have decimal places and need to be greater than 0.001)");
            jTextFieldAmount.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE) || 
                        c == '.')) {
                        e.consume();
                    }
                    if((c == '.') && jTextFieldAmount.getText().contains(".")){
                        e.consume();
                    }
                }
            });

            // stop loss and take profit panel
            jPanelSLandTP.setLayout(new  GridLayout(3, 3));
            // Buttongroup for pips or value selection
            // Radiobutton pips
            buttonGroupValuePip.add(jRadioButtonPip);
            jRadioButtonPip.setSelected(true);
            jRadioButtonPip.setText("Pip");
            jRadioButtonPip.setToolTipText("");
            jPanelSLandTP.add(jRadioButtonPip);
            // Label for stop loss
            jLabel1.setHorizontalAlignment( SwingConstants.RIGHT);
            jLabel1.setText("Stop Loss :");
            jPanelSLandTP.add(jLabel1);
            // Stop loss field and keys filter
            jTextFieldSL.setHorizontalAlignment( JTextField.RIGHT);
            jTextFieldSL.setText("0");
            jTextFieldSL.setToolTipText("Pips or value depending on the selection made at left");
            jPanelSLandTP.add(jTextFieldSL);
            jTextFieldSL.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE) || 
                        c == '.')) {
                        e.consume();
                    }
                    if((c == '.') && jTextFieldSL.getText().contains(".")){
                        e.consume();
                    }
                }
            });
            // Radiobutton value
            buttonGroupValuePip.add(jRadioButtonValue);
            jRadioButtonValue.setText("Value");
            jPanelSLandTP.add(jRadioButtonValue);
            // label for take profit
            jLabel2.setHorizontalAlignment( SwingConstants.RIGHT);
            jLabel2.setText("Take Profit :");
            jPanelSLandTP.add(jLabel2);
            // Take profit and keys filter 
            jTextFieldTP.setHorizontalAlignment( JTextField.RIGHT);
            jTextFieldTP.setText("0");
            jTextFieldTP.setToolTipText("Pips or value depending on the selection made at left");
            jPanelSLandTP.add(jTextFieldTP);
            jTextFieldTP.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE) || 
                        c == '.')) {
                        e.consume();
                    }
                    if((c == '.') && jTextFieldTP.getText().contains(".")){
                        e.consume();
                    }
                }
            });
            // no content
            jPanelSLandTP.add(fill);
            // Label for slippage
            jLabelSlipage.setHorizontalAlignment( SwingConstants.RIGHT);
            jLabelSlipage.setText("Slippage :");
            jPanelSLandTP.add(jLabelSlipage);
            // Slipage field and filters
            jTextFieldSlippage.setHorizontalAlignment( JTextField.RIGHT);
            jTextFieldSlippage.setText("0");
            jTextFieldSlippage.setToolTipText("Slippage in pips");
            jPanelSLandTP.add(jTextFieldSlippage);
            jTextFieldSlippage.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!((c >= '0') && (c <= '9') ||
                        (c == KeyEvent.VK_BACK_SPACE) ||
                        (c == KeyEvent.VK_DELETE) || 
                        c == '.')) {
                        e.consume();
                    }
                    if((c == '.') && jTextFieldSlippage.getText().contains(".")){
                        e.consume();
                    }
                }
            });

            // no horizontal scrollbar
            jScrollPane1.setHorizontalScrollBarPolicy( ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            // Jable border
            jTableOrders.setBorder( BorderFactory.createLineBorder(new  Color(0, 0, 0)));
            // Jtable model
            myDefTabMod = new DefaultTableModel(
                new Object [][] {},
                new String [] {
                    "<html><center>Order Label<br>Amount<br>Price</center></html>", 
                    "<html><center>Stop Loss<br>Take Profit<br>P/L in Pips</center></html>", 
                    "<html><center>Close<br>cliked<br>order</center></html>", "Orders ID", "Orders State"
                }
            ) { // user can't edit cell values
                boolean[] canEdit = new boolean [] {
                    false, false, false, false, false
                };

                @Override
                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return canEdit [columnIndex];
                }
            };
            jTableOrders.setModel(myDefTabMod);
            // Jtable settings
            jTableOrders.setRowSelectionAllowed(false);
            jScrollPane1.setViewportView(jTableOrders);
            jTableOrders.removeColumn(jTableOrders.getColumnModel().getColumn(4));
            jTableOrders.removeColumn(jTableOrders.getColumnModel().getColumn(3));
            ((JLabel) jTableOrders.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
            jTableOrders.getColumnModel().getColumn(0).setResizable(false);
            jTableOrders.getColumnModel().getColumn(1).setResizable(false);
            jTableOrders.getColumnModel().getColumn(2).setResizable(false);
            // Jtable color settings
            TableCellRenderer myCCC = new MyChangeCellColor();
            TableColumn myColumn0 = jTableOrders.getColumnModel().getColumn(0);
            TableColumn myColumn1 = jTableOrders.getColumnModel().getColumn(1);
            TableColumn myColumn2 = jTableOrders.getColumnModel().getColumn(2);
            myColumn0.setCellRenderer(myCCC);
            myColumn1.setCellRenderer(myCCC);
            myColumn2.setCellRenderer(myCCC);
            // mouse event listener for mouse clicks on table
            jTableOrders.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // get cell coordinates
                    int row = jTableOrders.rowAtPoint(e.getPoint());
                    int col = jTableOrders.columnAtPoint(e.getPoint());
                    if (row == -1 || col == -1){
                        return;
                    }
                    // stop loss cell
                    if ((row + 1) % 3 == 1 && col == 1){
                        String orderId = String.valueOf(jTableOrders.getModel().getValueAt(row, 3));
                        String orderLabel = String.valueOf(jTableOrders.getModel().getValueAt(row, 0));
                        double orderStopLoss = Double.parseDouble(String.valueOf(jTableOrders.getModel().getValueAt(row, col)));
                        Double newStopLoss = myInputBox(0, orderLabel, orderStopLoss);
                        IOrder order = myEngine.getOrderById(orderId);
                        if (!(newStopLoss.isNaN())){
                            try {
                                order.setStopLossPrice(newStopLoss);
                            } catch (JFException ex) {
                                Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    // take profit cell
                    if ((row + 1) % 3 == 2 && col == 1){
                        String orderId = String.valueOf(jTableOrders.getModel().getValueAt(row, 3));
                        String orderLabel = String.valueOf(jTableOrders.getModel().getValueAt(row - 1, 0));
                        double orderTakeProfit = Double.parseDouble(String.valueOf(jTableOrders.getModel().getValueAt(row, col)));
                        Double newTakeProfit = myInputBox(1, orderLabel, orderTakeProfit);
                        IOrder order = myEngine.getOrderById(orderId);
                        if (!(newTakeProfit.isNaN())){
                            try {
                                order.setTakeProfitPrice(newTakeProfit);
                            } catch (JFException ex) {
                                Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    // close column and close the associated order
                    if (col == 2){
                        String orderId = String.valueOf(jTableOrders.getModel().getValueAt(row, 3));
                        IOrder order = myEngine.getOrderById(orderId);
                        if (order == null){
                            try {
                                synchronize();
                            } catch (JFException ex) {
                                Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            return;
                        }
                        try {
                            order.close();
                        } catch (JFException ex) {
                            Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                /**
                 * myInputBox Function
                 * 
                 * Shows a jOptionPane for user input of stop loss or Take Profit
                 */
                private double myInputBox(int i, String orderLabel, double orderValue) {
                    String[] boxMessages = new String[2];
                    boxMessages[0] = "Insert new value for Stop Loss for Order label [" + orderLabel + "]:";
                    boxMessages[1] = "Insert new value for Take Profit for Order label [" + orderLabel + "]:";
                    String[] boxTitle = new String[2];
                    boxTitle[0] = "Actual Stop Loss: " + orderValue;
                    boxTitle[1] = "Actual Take Profit: " + orderValue;
                    String inputresult = JOptionPane.showInputDialog(null, boxMessages[i], boxTitle[i], JOptionPane.QUESTION_MESSAGE);
                    if (inputresult == null){
                        return Double.NaN;
                    }
                    if (!isDouble(inputresult)){
                        myInputBox(i, orderLabel, orderValue);
                    }
                    return Double.parseDouble(inputresult);
                }

                /**
                 * isDouble Function
                 * 
                 * To validate user input
                 */
                private boolean isDouble(String inputresult) {
                    try{
                        Double.parseDouble(inputresult);
                        return true;
                    }catch (Exception e){
                        return false;
                    }
                }
            });
            
            // Table title
            jPanelTitles.setLayout(new  GridLayout(1, 2));
            // Title
            jLabel3.setFont(new  Font("Tahoma", 1, 14)); 
            jLabel3.setText("Orders");
            jPanelTitles.add(jLabel3);
            // Button synchronize
            jButtonSynchronize.setText("Synchronize");
            jPanelTitles.add(jButtonSynchronize);
            
            // positioning of all components on the window
            GroupLayout layout = new  GroupLayout(getContentPane());
            getContentPane().setLayout(layout);
            layout.setHorizontalGroup(
                layout.createParallelGroup( GroupLayout.Alignment.LEADING)
                .addComponent(jPanelPrices,  GroupLayout.DEFAULT_SIZE,  GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanelButtons,  GroupLayout.DEFAULT_SIZE,  GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanelEntry,  GroupLayout.DEFAULT_SIZE,  GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanelOptions,  GroupLayout.Alignment.TRAILING,  GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(jPanelSLandTP,  GroupLayout.DEFAULT_SIZE,  GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jScrollPane1,  GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addComponent(jPanelTitles,  GroupLayout.DEFAULT_SIZE,  GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            );
            layout.setVerticalGroup(
                layout.createParallelGroup( GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(jPanelPrices,  GroupLayout.PREFERRED_SIZE, 50,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jPanelButtons,  GroupLayout.PREFERRED_SIZE,  GroupLayout.DEFAULT_SIZE,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jPanelOptions,  GroupLayout.PREFERRED_SIZE,  GroupLayout.DEFAULT_SIZE,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jPanelEntry,  GroupLayout.PREFERRED_SIZE,  GroupLayout.DEFAULT_SIZE,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jPanelSLandTP,  GroupLayout.PREFERRED_SIZE,  GroupLayout.DEFAULT_SIZE,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jPanelTitles,  GroupLayout.PREFERRED_SIZE,  GroupLayout.DEFAULT_SIZE,  GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap( LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(jScrollPane1,  GroupLayout.DEFAULT_SIZE, 297, Short.MAX_VALUE))
            );

            pack();
        }// end initComponents

        /**
         * actionPerformed Function
         * 
         * Window events for radiobuttons and buttons
         * 
         * @param e 
         */
        @Override
        public void actionPerformed(ActionEvent e){
            if (e.getSource() == jRadioButtonAsk){
                jTextFieldEntry.setEnabled(true);
            }
            if (e.getSource() == jRadioButtonBid){
                jTextFieldEntry.setEnabled(true);
            }
            if (e.getSource() == jRadioButtonLimit){
                jTextFieldEntry.setEnabled(true);
            }
            if (e.getSource() == jRadioButtonMarket){
                jTextFieldEntry.setText(null);
                jTextFieldEntry.setEnabled(false);
            }
            if (e.getSource() == jButtonBuy){
                boolean fieldsOk = validateFields();
                if (!fieldsOk){
                    return;
                }
                try {
                    placeOrder("BUY");
                } catch (JFException ex) {
                    Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (e.getSource() == jButtonSell){
                boolean fieldsOk = validateFields();
                if (!fieldsOk){
                    return;
                }
                try {
                    placeOrder("SELL");
                } catch (JFException ex) {
                    Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (e.getSource() == jButtonSynchronize){
                try {
                    synchronize();
                } catch (JFException ex) {
                    Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }// end actionPerformed
        
        
        /**
         * updatePrices Function
         * 
         * Update the Labels of bid and ask price
         * 
         * @param tick 
         */
        public void updatePrices(ITick tick){
            jLabelPriceBid.setText(Double.toString(tick.getBid()));
            jLabelPriceAsk.setText(Double.toString(tick.getAsk()));
        }// end updatePrices

        /**
         * Validate the textfields values and return true if ok
         * 
         * @return true if text fields ok, false otherwise 
         */
        private boolean validateFields(){
            if (jTextFieldEntry.getText() != null && !jRadioButtonMarket.isSelected()){
                try {
                    myEntry = Double.valueOf(jTextFieldEntry.getText());
                    if (myEntry < 0){
                        JOptionPane.showMessageDialog(null, "Incorrect entry value!", "Incorrect entry value...", JOptionPane.WARNING_MESSAGE);
                        jTextFieldEntry.requestFocusInWindow();
                        return false;
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Incorrect entry value!", "Incorrect entry value...", JOptionPane.WARNING_MESSAGE);
                    jTextFieldEntry.requestFocusInWindow();
                    return false;
                }
            }
            try {
                myAmount = Double.valueOf(jTextFieldAmount.getText());
                if (myAmount < 0){
                    JOptionPane.showMessageDialog(null, "Incorrect amount value!", "Incorrect amount value...", JOptionPane.WARNING_MESSAGE);
                    jTextFieldAmount.requestFocusInWindow();
                    return false;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Incorrect amount value!", "Incorrect amount value...", JOptionPane.WARNING_MESSAGE);
                jTextFieldAmount.requestFocusInWindow();
                return false;
            }
            try {
                myStopLoss = Double.valueOf(jTextFieldSL.getText());
                if (myStopLoss < 0){
                    JOptionPane.showMessageDialog(null, "Incorrect stop loss value!", "Incorrect stop loss value...", JOptionPane.WARNING_MESSAGE);
                    jTextFieldSL.requestFocusInWindow();
                    return false;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Incorrect stop loss value!", "Incorrect stop loss value...", JOptionPane.WARNING_MESSAGE);
                jTextFieldSL.requestFocusInWindow();
                return false;
            }
            try {
                myTakeProfit = Double.valueOf(jTextFieldTP.getText());
                if (myTakeProfit < 0){
                    JOptionPane.showMessageDialog(null, "Incorrect take profit value!", "Incorrect take profit value...", JOptionPane.WARNING_MESSAGE);
                    jTextFieldTP.requestFocusInWindow();
                    return false;
                }
                if (myTakeProfit == 0d) {
                    myTakeProfit = Double.NaN;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Incorrect take profit value!", "Incorrect take profit value...", JOptionPane.WARNING_MESSAGE);
                jTextFieldTP.requestFocusInWindow();
                return false;
            }
            try {
                mySlippage = Double.valueOf(jTextFieldSlippage.getText());
                if (mySlippage < 0){
                    JOptionPane.showMessageDialog(null, "Incorrect slippage value!", "Incorrect slippage value...", JOptionPane.WARNING_MESSAGE);
                    jTextFieldSlippage.requestFocusInWindow();
                    return false;
                }
                if (mySlippage == 0d){
                    mySlippage = Double.NaN;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Incorrect slippage value!", "Incorrect slippage value...", JOptionPane.WARNING_MESSAGE);
                jTextFieldSlippage.requestFocusInWindow();
                return false;
            }
            return true;
        }// end validateprices

        /**
         * Submits the orders
         * 
         * @param orderType direction of the order 
         * @throws JFException 
         */
        private void placeOrder(String orderType) throws JFException {
            OrderCommand myOrderCommand = null;
            if ("BUY".equals(orderType)){
                if (jRadioButtonMarket.isSelected() == true){
                    myOrderCommand = OrderCommand.BUY;
                }
                if (jRadioButtonBid.isSelected() == true){
                    myOrderCommand = OrderCommand.BUYSTOP_BYBID;
                }
                if (jRadioButtonAsk.isSelected() == true){
                    myOrderCommand = OrderCommand.BUYSTOP;
                }
                if (jRadioButtonLimit.isSelected() == true){
                    myOrderCommand = OrderCommand.BUYLIMIT;
                }
            }else{
                if (jRadioButtonMarket.isSelected() == true){
                    myOrderCommand = OrderCommand.SELL;
                }
                if (jRadioButtonBid.isSelected() == true){
                    myOrderCommand = OrderCommand.SELLSTOP;
                }
                if (jRadioButtonAsk.isSelected() == true){
                    myOrderCommand = OrderCommand.SELLSTOP_BYASK;
                }
                if (jRadioButtonLimit.isSelected() == true){
                    myOrderCommand = OrderCommand.SELLLIMIT;
                }
            }
            if (jRadioButtonMarket.isSelected()){
                myOrder = myEngine.submitOrder(orderType + myOrderCounter, myInstrument, myOrderCommand, myAmount, 0, mySlippage);
                myOrderCounter++;
            } else {
                myOrder = myEngine.submitOrder(orderType + myOrderCounter, myInstrument, myOrderCommand, myAmount, myEntry, mySlippage);
                myOrderCounter++;
            }
        }// end placeOrder
        
        /**
         * Sets the Stop Loss and take profit of any order provided
         * 
         * @param order to change SL and TP 
         * @throws JFException 
         */
        public void setTpSl(IOrder order) throws JFException{
            if (myStopLoss > 0 && jRadioButtonPip.isSelected()){
                double orderSL;
                if ("BUY".equals(order.getOrderCommand().toString()) || "BUYSTOP".equals(order.getOrderCommand().toString()) || 
                        "BUYSTOP_BYBID".equals(order.getOrderCommand().toString()) || "BUYLIMIT".equals(order.getOrderCommand().toString())){
                    orderSL = myOrder.getOpenPrice() - myStopLoss * myPipValue;
                    order.setStopLossPrice(orderSL);
                    
                } else {
                    orderSL = myOrder.getOpenPrice() + myStopLoss * myPipValue;
                    order.setStopLossPrice(orderSL);
                    
                }
            }
            if (myStopLoss > 0 && jRadioButtonValue.isSelected()){
                order.setStopLossPrice(myStopLoss);
            }
            if (myTakeProfit > 0 && jRadioButtonPip.isSelected()){
                double orderTP;
                if ("BUY".equals(order.getOrderCommand().toString()) || "BUYSTOP".equals(order.getOrderCommand().toString()) ||
                        "BUYSTOP_BYBID".equals(order.getOrderCommand().toString()) || "BUYLIMIT".equals(order.getOrderCommand().toString())){
                    orderTP = myOrder.getOpenPrice() + myTakeProfit * myPipValue;
                    order.setTakeProfitPrice(orderTP);
                } else {
                    orderTP = myOrder.getOpenPrice() - myTakeProfit * myPipValue;
                    order.setTakeProfitPrice(orderTP);
                    
                }
            }
            if (myTakeProfit > 0 && jRadioButtonValue.isSelected()){
                order.setTakeProfitPrice(myTakeProfit);
            }
            myTakeProfit = 0;
            myStopLoss = 0;
        }// end setTpSl
        
        /**
         * Inserts the order values on the table of any given order
         * 
         * @param order to add to the table 
         */
        public void createTableOrder(IOrder order){
            myDefTabMod.addRow(new Object[] {order.getLabel(), order.getStopLossPrice(), "CLOSE", order.getId(), order.getState().toString()});
            myDefTabMod.addRow(new Object[] {order.getAmount(), order.getTakeProfitPrice(), "CLOSE", order.getId(), order.getState().toString()});
            myDefTabMod.addRow(new Object[] {order.getOpenPrice(), order.getProfitLossInPips(), "CLOSE", order.getId(), order.getState().toString()});
        }// end createTableOrder
        
        /**
         * Update the order value on the table
         * 
         * @param order to update
         */
        public void updateTableOrder(IOrder order){
            if (order == null){
                try {
                    synchronize();
                } catch (JFException ex) {
                    Logger.getLogger(myOfflineTrades.class.getName()).log(Level.SEVERE, null, ex);
                }
                return;
            }
            double profitLossInPips = order.getProfitLossInPips();
            double orderPrice = order.getOpenPrice();
            double orderAmount = order.getAmount();
            double orderStopLoss = order.getStopLossPrice();
            double orderTakeProfit = order.getTakeProfitPrice();
            String orderState = order.getState().toString();
            String orderID = order.getId();
           
            for(int row = 0; row < jTableOrders.getRowCount(); row++){ 
                if (orderID.equals(jTableOrders.getModel().getValueAt(row, 3))){
                    jTableOrders.getModel().setValueAt(orderState, row, 4);
                    if ((row + 1) % 3 == 1){
                        if (!jTableOrders.isCellSelected(row, 1)){
                            jTableOrders.getModel().setValueAt(orderStopLoss, row, 1);
                        }
                    }
                    if ((row + 1) % 3 == 2){
                        jTableOrders.getModel().setValueAt(orderAmount, row, 0);
                        if (!jTableOrders.isCellSelected(row, 1)){
                            jTableOrders.getModel().setValueAt(orderTakeProfit, row, 1);
                        }
                    }
                    if ((row + 1) % 3 == 0){
                        jTableOrders.getModel().setValueAt(orderPrice, row, 0);
                        jTableOrders.getModel().setValueAt(profitLossInPips, row, 1);
                        return;
                    }
                }
            }
        }// end updateTableOrder
        
        /**
         * Remove provided order from the table
         * 
         * @param orderId of the order to remove 
         */
        public void removeTableOrder(String orderId){
            for(int row = 0; row < jTableOrders.getRowCount(); row++){
                if (orderId.equals(jTableOrders.getModel().getValueAt(row, 3))){
                    myDefTabMod.removeRow(row + 2);
                    myDefTabMod.removeRow(row + 1);
                    myDefTabMod.removeRow(row);
                }
                return;
            }
        }// end removeTableOrder
        
        /**
         * Deletes all orders from the table and fills again with all active 
         * orders
         * 
         * Note: Workarround to some orders not correcctly removed - need 
         * analisys why
         * @throws JFException 
         */
        public void synchronize() throws JFException{
            myDefTabMod.getDataVector().removeAllElements();
            myDefTabMod.fireTableDataChanged();
            List<IOrder> openOrders;
            openOrders = myEngine.getOrders();
            for (IOrder order : openOrders){
                createTableOrder(order);
            }
        }
        /**
         * for some debugging
         * 
         * @param string 
         */        
        public void print(String string){
            myConsole.getOut().println(string);
        }// end print
    }// end class offlineForm
    
    /**
     * Alligns the cell contents to the center
     */
    class MyTableUtilsCenter extends DefaultTableCellRenderer {

        public MyTableUtilsCenter() {
            setHorizontalAlignment(CENTER);
        }
    }// end class MyTableUtilsCenter

    /**
     * Sets the foreground/background colors of the cells
     */
    class MyChangeCellColor extends JLabel implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            setHorizontalAlignment(CENTER);
            setOpaque(true);
            if(column == 0){
                if((row + 1) % 3 == 1){
                    setBackground(Color.LIGHT_GRAY);
                    if("OPENED".equals(table.getModel().getValueAt(row, 4))){
                        setBackground(Color.YELLOW);
                    }
                    if("FILLED".equals(table.getModel().getValueAt(row, 4))){
                        setBackground(Color.GREEN);
                    }
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 2){
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 0){
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.BLACK);
                }
            }
            if(column == 1){
                if((row + 1) % 3 == 1){
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 2){
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 0){
                    //TODO try to block edit possibility
                    setBackground(Color.LIGHT_GRAY);
                    if (value != null){
                        if (Double.parseDouble(String.valueOf(value)) > 0) {
                            setBackground(Color.GREEN);
                        }
                        if (Double.parseDouble(String.valueOf(value)) < 0) {
                            setBackground(Color.RED);
                        }
                        if (Double.parseDouble(String.valueOf(value)) == 0) {
                            setBackground(Color.YELLOW);
                        }   
                    }
                    
                }
            }
            if(column == 2){
                if((row + 1) % 3 == 1){
                    setBackground(Color.LIGHT_GRAY);
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 2){
                    setBackground(Color.GRAY);
                    setForeground(Color.BLACK);
                }
                if((row + 1) % 3 == 0){
                    setBackground(Color.BLACK);
                    setForeground(Color.WHITE);
                }
            }
            setText(value.toString());
            return this;
        }
    }//end MyChangeCell Color
    
    
}
