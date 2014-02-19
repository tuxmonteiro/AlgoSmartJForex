package jforex.strategies.indicators;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.indicators.IIndicator;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SMACrossover implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private int counter = 0;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.ONE_HOUR;
    @Configurable("Short MA type")
    public MaType shortMAType = MaType.SMA;
    @Configurable("Short MA time period")
    public int shortMAPeriod = 5;
    @Configurable("Long MA type")
    public MaType longMAType = MaType.SMA;
    @Configurable("Long MA time period")
    public int longMAPeriod = 30;
    @Configurable("Limit order #")
    public int limitOrdCount = 3;
    @Configurable("Stop order #")
    public int stopOrderCount = 3;
    @Configurable("Take profit USD")
    public double takeProfitUSD = 250;
    @Configurable("Stop loss USD")
    public double stopLossUSD = 150;
    @Configurable("Offer side")
    public OfferSide offerSide = OfferSide.BID;
    @Configurable("Applied price")
    public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
    @Configurable("Applied price")
    public Filter filter = Filter.ALL_FLATS;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Amount")
    public double amount = 0.02;
//    @Configurable("Stop loss in pips") 
//    public int stopLossPips = 30;
//    @Configurable("Take profit pips")
//    public int takeProfitPips = 30;
    private List<IOrder> orders = new ArrayList<IOrder>();

    public void onStart(IContext context) throws JFException {

        this.engine = context.getEngine();
        this.history = context.getHistory();
        this.indicators = context.getIndicators();
        this.console = context.getConsole();

        IChart chart = context.getChart(instrument);
        if (chart != null) {
            chart.addIndicator(indicators.getIndicator("MA"), new Object[]{shortMAPeriod, shortMAType.ordinal()});
            chart.addIndicator(indicators.getIndicator("MA"), new Object[]{longMAPeriod, longMAType.ordinal()});
        }
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onStop() throws JFException {
        // close all orders
        for (IOrder order : engine.getOrders()) {
            engine.getOrder(order.getLabel()).close();
        }
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (!instrument.equals(this.instrument)) {
            return;
        }


        if (orders.size() > 0) {
            
            double profitLoss = 0;
            for (IOrder order : engine.getOrders()) {
                profitLoss += order.getProfitLossInUSD();
            }
            boolean isLong = orders.get(0).isLong();
            boolean resubmit = false;

            if (profitLoss > takeProfitUSD) {
                print("close all on take profit");
                
                for (IOrder order : orders) {
                    closeOrder(order);
                }
                orders.clear();
                
            } else if (profitLoss < -stopLossUSD) {
                print("close all on stop loss");
                
                for (IOrder order : orders) {
                    closeOrder(order);
                }
                orders.clear();
                resubmit = true;
            }
            
            if(resubmit) {
                if(isLong) {
                    orders.add(submitOrder(OrderCommand.BUY, tick.getAsk()));
                    for (int i = 1; i <= limitOrdCount; i++) {
                        orders.add(submitOrder(OrderCommand.BUYLIMIT, tick.getAsk() - 10 * getPipPrice(i, instrument)));
                    }

                    for (int i = 1; i <= stopOrderCount; i++) {
                        orders.add(submitOrder(OrderCommand.BUYSTOP, tick.getAsk() + 10 * getPipPrice(i, instrument)));
                    }

                } else {
                    orders.add(submitOrder(OrderCommand.SELL, tick.getBid()));
                    for (int i = 1; i <= limitOrdCount; i++) {
                        orders.add(submitOrder(OrderCommand.SELLLIMIT, tick.getBid() + 10 * getPipPrice(i, instrument)));
                    }

                    for (int i = 1; i <= stopOrderCount; i++) {
                        orders.add(submitOrder(OrderCommand.SELLSTOP, tick.getBid() - 10 * getPipPrice(i, instrument)));
                    }
                }
            }
            
        }


    }

    public void onBar(Instrument instrument, Period period, IBar askBar,
            IBar bidBar) throws JFException {

        if (!instrument.equals(this.instrument)
                || !period.equals(this.selectedPeriod)) {
            return;
        }

        // open order signals with MA
        double[] shortMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, shortMAPeriod, shortMAType, filter, 2, askBar.getTime(), 0);
        double[] longMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, longMAPeriod, longMAType, filter, 2, askBar.getTime(), 0);
        int CURRENT = 1;
        int PREVIOUS = 0;

        if (shortMA[PREVIOUS] >= longMA[PREVIOUS] && shortMA[CURRENT] < longMA[CURRENT]) {
            for (IOrder order : orders) {
                closeOrder(order);
            }
            orders.clear();

            ITick tick = history.getLastTick(instrument);

            orders.add(submitOrder(OrderCommand.SELL, tick.getBid()));
            for (int i = 1; i <= limitOrdCount; i++) {
                orders.add(submitOrder(OrderCommand.SELLLIMIT, tick.getBid() + 10 * getPipPrice(i, instrument)));
            }

            for (int i = 1; i <= stopOrderCount; i++) {
                orders.add(submitOrder(OrderCommand.SELLSTOP, tick.getBid() - 10 * getPipPrice(i, instrument)));
            }

        } else if (shortMA[PREVIOUS] <= longMA[PREVIOUS] && shortMA[CURRENT] > longMA[CURRENT]) {
            for (IOrder order : orders) {
                closeOrder(order);
            }
            orders.clear();

            ITick tick = history.getLastTick(instrument);

            orders.add(submitOrder(OrderCommand.BUY, tick.getAsk()));
            for (int i = 1; i <= limitOrdCount; i++) {
                orders.add(submitOrder(OrderCommand.BUYLIMIT, tick.getAsk() - 10 * getPipPrice(i, instrument)));
            }

            for (int i = 1; i <= stopOrderCount; i++) {
                orders.add(submitOrder(OrderCommand.BUYSTOP, tick.getAsk() + 10 * getPipPrice(i, instrument)));
            }
        }
    }

    private void closeOrder(IOrder order) throws JFException {
        if (order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CANCELED) {
            order.close();
        }
    }

    private IOrder submitOrder(OrderCommand orderCmd, double price) throws JFException {

        double stopLossPrice = 0, takeProfitPrice = 0;

//        // Calculating stop loss and take profit prices 
//        if (orderCmd == OrderCommand.BUY) {
//            stopLossPrice = price - getPipPrice(this.stopLossPips, instrument);
//            takeProfitPrice = price + getPipPrice(this.takeProfitPips, instrument);
//            
//        } else {
//            stopLossPrice = price + getPipPrice(this.stopLossPips, instrument);
//            takeProfitPrice = price - getPipPrice(this.takeProfitPips, instrument);
//        }
        // Submitting an order for the specified instrument at the current market price
        return engine.submitOrder(getLabel(instrument), this.instrument, orderCmd, this.amount, price, slippage, stopLossPrice, takeProfitPrice);
    }

    protected String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    private double getPipPrice(double pips, Instrument instr) {
        return pips * instr.getPipValue();
    }

    private void print(Object... o) {
        for (Object ob : o) {
            console.getOut().print(ob + "  ");
        }
        console.getOut().println();
    }

    private void print(Object o) {
        console.getOut().println(o);
    }

    private void print(double[] arr) {
        print(arrayToString(arr));
    }

    private void print(double[][] arr) {
        print(arrayToString(arr));
    }

    private void printIndicatorInfos(IIndicator ind) {
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfInputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Input " + ind.getInputParameterInfo(i).getName() + " " + ind.getInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOptionalInputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Opt Input " + ind.getOptInputParameterInfo(i).getName() + " " + ind.getOptInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOutputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Output " + ind.getOutputParameterInfo(i).getName() + " " + ind.getOutputParameterInfo(i).getType());
        }
    }

    public static String arrayToString(double[] arr) {
        String str = "";
        for (int r = 0; r < arr.length; r++) {
            str += "[" + r + "] " + (new DecimalFormat("#.#######")).format(arr[r]) + "; ";
        }
        return str;
    }

    public static String arrayToString(double[][] arr) {
        String str = "";
        if (arr == null) {
            return "null";
        }
        for (int r = 0; r < arr.length; r++) {
            for (int c = 0; c < arr[r].length; c++) {
                str += "[" + r + "][" + c + "] " + (new DecimalFormat("#.#######")).format(arr[r][c]);
            }
            str += "; ";
        }
        return str;
    }
    // ________________________________
}
