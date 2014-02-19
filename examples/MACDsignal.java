package jforex.strategies.indicators;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.indicators.IIndicator;

public class MACDsignal implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private int counter = 0;
    private IOrder order;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.TEN_MINS;
    @Configurable("Offer side")
    public OfferSide offerSide = OfferSide.BID;
    @Configurable("Slippage")
    public double slippage = 1;
    @Configurable("Amount")
    public double amount = 0.02;
    @Configurable("Take profit pips")
    public int takeProfitPips = 50;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 10;
    @Configurable("Applied price")
    public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
    @Configurable("Fast period")
    public int fastMACDPeriod = 12;
    @Configurable("Slow period")
    public int slowMACDPeriod = 26;
    @Configurable("Signal period")
    public int signalMACDPeriod = 9;
    @SuppressWarnings("serial")
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {

        {
            setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    };

    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();

        IChart chart = context.getChart(instrument);
        if (chart != null) {
            chart.addIndicator(indicators.getIndicator("MACD"), new Object[]{fastMACDPeriod, slowMACDPeriod, signalMACDPeriod});
        }
    }
    public static final int HIST = 2;

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (period != this.selectedPeriod || instrument != this.instrument) {
            return;
        }

        double[] macd0 = indicators.macd(instrument, this.selectedPeriod, offerSide, appliedPrice, fastMACDPeriod, slowMACDPeriod, signalMACDPeriod, 0);
        double[] macd1 = indicators.macd(instrument, this.selectedPeriod, offerSide, appliedPrice, fastMACDPeriod, slowMACDPeriod, signalMACDPeriod, 1);

        if (macd0[HIST] > 0 && macd1[HIST] <= 0) {
            closeOrder(order);
            order = submitOrder(OrderCommand.BUY);
        }
        if (macd0[HIST] < 0 && macd1[HIST] >= 0) {
            closeOrder(order);
            order = submitOrder(OrderCommand.SELL);
        }
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != this.instrument) {
            return;
        }
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onStop() throws JFException {
    }

    private IOrder submitOrder(OrderCommand orderCmd) throws JFException {

        double stopLossPrice, takeProfitPrice;

        // Calculating order price, stop loss and take profit prices
        if (orderCmd == OrderCommand.BUY) {
            stopLossPrice = history.getLastTick(this.instrument).getBid() - getPipPrice(this.stopLossPips);
            takeProfitPrice = history.getLastTick(this.instrument).getBid() + getPipPrice(this.takeProfitPips);
        } else {
            stopLossPrice = history.getLastTick(this.instrument).getAsk() + getPipPrice(this.stopLossPips);
            takeProfitPrice = history.getLastTick(this.instrument).getAsk() - getPipPrice(this.takeProfitPips);
        }

        return engine.submitOrder(getLabel(instrument), instrument, orderCmd, amount, 0, 20, stopLossPrice, takeProfitPrice);
    }

    private void closeOrder(IOrder order) throws JFException {
        if (order == null) {
            return;
        }
        if (order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CREATED && order.getState() != IOrder.State.CANCELED) {
            order.close();
            order = null;
        }
    }

    private double getPipPrice(int pips) {
        return pips * this.instrument.getPipValue();
    }

    private String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    private void print(Object... o) {
        for (Object ob : o) {
            //console.getOut().print(ob + "  ");
            if (ob instanceof double[]) {
                print((double[]) ob);
            } else if (ob instanceof double[]) {
                print((double[][]) ob);
            } else if (ob instanceof Long) {
                print(dateToStr((Long) ob));
            } else {
                print(ob);
            }
            print(" ");
        }
        console.getOut().println();
    }

    private void print(Object o) {
        console.getOut().print(o);
    }

    private void println(Object o) {
        console.getOut().println(o);
    }

    private void print(double[] arr) {
        println(arrayToString(arr));
    }

    private void print(double[][] arr) {
        println(arrayToString(arr));
    }

    private void printIndicatorInfos(IIndicator ind) {
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfInputs(); i++) {
            println(ind.getIndicatorInfo().getName() + " Input " + ind.getInputParameterInfo(i).getName() + " " + ind.getInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOptionalInputs(); i++) {
            println(ind.getIndicatorInfo().getName() + " Opt Input " + ind.getOptInputParameterInfo(i).getName() + " " + ind.getOptInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOutputs(); i++) {
            println(ind.getIndicatorInfo().getName() + " Output " + ind.getOutputParameterInfo(i).getName() + " " + ind.getOutputParameterInfo(i).getType());
        }
        console.getOut().println();
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

    public String toDecimalToStr(double d) {
        return (new DecimalFormat("#.#######")).format(d);
    }

    public String dateToStr(Long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {

            {
                setTimeZone(TimeZone.getTimeZone("GMT"));
            }
        };
        return sdf.format(time);
    }
}
