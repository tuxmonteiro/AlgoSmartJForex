package jforex.strategies.indicators;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.indicators.IIndicator;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OppositeOrder implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private IAccount account;
    private int counter = 0;
    private IOrder order;
    private IOrder pendingOrder;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.TEN_MINS;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Amount")
    public double amount = 0.02;
    @Configurable("Place long first")
    public boolean nextLong = true;
    @Configurable("Take profit pips")
    public int takeProfitPips = 20;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 20;
    
    public double dynamicAmount = amount;

    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (period != this.selectedPeriod || instrument != this.instrument) {
            return;
        }
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != this.instrument) {
            return;
        }

        if (!isActive(order)) {
            order = null;

            if (pendingOrder != null) {
                
                if(pendingOrder.getState() == IOrder.State.FILLED) {
                    // STOP LOSS
                    order = pendingOrder;
                    dynamicAmount = dynamicAmount * 2;

                    if (order.isLong()) {
                        nextLong = true; // switch side
                        pendingOrder = submitOrder(OrderCommand.SELLSTOP, instrument, order.getStopLossPrice());
                        
                    } else {
                        nextLong = false; // switch side
                        pendingOrder = submitOrder(OrderCommand.BUYSTOP, instrument, order.getStopLossPrice());
                    }
                } else {
                    dynamicAmount = amount;
                    // TAKE PROFIT, close opposite
                    closeOrder(pendingOrder);
                }
            }
        }

        // RESTART
        if (nextLong) {
            if (order == null) {
                // BUY
                order = submitOrder(OrderCommand.BUY, instrument);
                dynamicAmount = dynamicAmount * 2;
                pendingOrder = submitOrder(OrderCommand.SELLSTOP, instrument, order.getStopLossPrice());
            }

        } else {
            if (order == null) {
                // SELL
                order = submitOrder(OrderCommand.SELL, instrument);
                dynamicAmount = dynamicAmount * 2;
                pendingOrder = submitOrder(OrderCommand.BUYSTOP, instrument, order.getStopLossPrice());
            }
        }

    }

    private IOrder submitOrder(OrderCommand orderCmd, Instrument instr) throws JFException {

        double stopLossPrice = 0.0, takeProfitPrice = 0.0;

        // Calculating order price, stop loss and take profit prices
        if (orderCmd.isLong()) {
            double tmp = history.getLastTick(instr).getBid();

            if (stopLossPips > 0) {
                stopLossPrice = tmp - getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = tmp + getPipPrice(takeProfitPips);
            }

        } else {
            double tmp = history.getLastTick(instr).getAsk();

            if (stopLossPips > 0) {
                stopLossPrice = tmp + getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = tmp - getPipPrice(takeProfitPips);
            }
        }

        return engine.submitOrder(getLabel(instr), instr, orderCmd, dynamicAmount, 0, slippage, getRoundedPrice(stopLossPrice), getRoundedPrice(takeProfitPrice));
    }

    private IOrder submitOrder(OrderCommand orderCmd, Instrument instr, double price) throws JFException {

        double stopLossPrice = 0.0, takeProfitPrice = 0.0;

        // Calculating order price, stop loss and take profit prices
        if (orderCmd.isLong()) {
            if (stopLossPips > 0) {
                stopLossPrice = price - getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = price + getPipPrice(takeProfitPips);
            }

        } else {
            if (stopLossPips > 0) {
                stopLossPrice = price + getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = price - getPipPrice(takeProfitPips);
            }
        }

        return engine.submitOrder(getLabel(instr), instr, orderCmd, dynamicAmount, price, slippage, getRoundedPrice(stopLossPrice), getRoundedPrice(takeProfitPrice));

    }

    private void closeOrder(IOrder order) throws JFException {
        if (order != null && isActive(order)) {
            order.close();
        }
    }

    private boolean isActive(IOrder order) throws JFException {
        if (order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CREATED && order.getState() != IOrder.State.CANCELED) {
            return true;
        }
        return false;
    }

    private double getPipPrice(double pips) {
        return pips * this.instrument.getPipValue();
    }

    private String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    private double getRoundedPrice(double price) {
        BigDecimal bd = new BigDecimal(price);
        bd = bd.setScale(instrument.getPipScale() + 1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private double getRoundedPips(double pips) {
        BigDecimal bd = new BigDecimal(pips);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onStop() throws JFException {
    }

    /**************** debug print functions ***********************/
    private void print(Object... o) {
        for (Object ob : o) {
            //console.getOut().print(ob + "  ");
            if (ob instanceof Double) {
                print2(toStr((Double) ob));
            } else if (ob instanceof double[]) {
                print((double[]) ob);
            } else if (ob instanceof double[]) {
                print((double[][]) ob);
            } else if (ob instanceof Long) {
                print2(toStr((Long) ob));
            } else if (ob instanceof IBar) {
                print2(toStr((IBar) ob));
            } else {
                print2(ob);
            }
            print2(" ");
        }
        console.getOut().println();
    }

    private void print(Object o) {
        console.getOut().println(o);
    }

    private void print2(Object o) {
        console.getOut().print(o);
    }

    private void print(double d) {
        print(toStr(d));
    }

    private void print(double[] arr) {
        print(toStr(arr));
    }

    private void print(double[][] arr) {
        print(toStr(arr));
    }

    private void print(IBar bar) {
        print(toStr(bar));
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
        console.getOut().println();
    }

    public static String toStr(double[] arr) {
        String str = "";
        for (int r = 0; r < arr.length; r++) {
            str += "[" + r + "] " + (new DecimalFormat("#.#######")).format(arr[r]) + "; ";
        }
        return str;
    }

    public static String toStr(double[][] arr) {
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

    public String toStr(double d) {
        return (new DecimalFormat("#.#######")).format(d);
    }

    public String toStr(Long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {

            {
                setTimeZone(TimeZone.getTimeZone("GMT"));
            }
        };
        return sdf.format(time);
    }

    private String toStr(IBar bar) {
        return toStr(bar.getTime()) + "  O:" + bar.getOpen() + " C:" + bar.getClose() + " H:" + bar.getHigh() + " L:" + bar.getLow();
    }

    private void printTime(Long time) {
        console.getOut().println(toStr(time));
    }
}
