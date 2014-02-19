package jforex.strategies;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.indicators.IIndicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class PrecedentPeriodHighLow implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;

    private int counter = 0;
    
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.ONE_HOUR;
    @Configurable("Offer side")
    public OfferSide offerSide = OfferSide.BID;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Amount")
    public double amount = 0.02;
    @Configurable("Applied price")
    public AppliedPrice appliedPrice = AppliedPrice.CLOSE;

    @Configurable("Take profit pips")
    public int takeProfitPips = 10;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 10;

    private IOrder buyOrder;
    private IOrder sellOrder;
    

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

        if (!isActive(buyOrder)) {
            buyOrder = null;
        }
        if (!isActive(sellOrder)) {
            sellOrder = null;
        }

        ITick tick = history.getLastTick(instrument);


        if(buyOrder == null) {
            double buyPrice = offerSide.equals(OfferSide.ASK) ? askBar.getHigh() : bidBar.getHigh();
            if(tick.getAsk() < buyPrice) {
                buyOrder = submitOrder(OrderCommand.BUYSTOP, instrument, buyPrice, takeProfitPips, stopLossPips);
            }
        }

        if(sellOrder == null) {
            double sellPrice = offerSide.equals(OfferSide.ASK) ? askBar.getLow() : bidBar.getLow();
            if(tick.getBid() > sellPrice) {
                sellOrder = submitOrder(OrderCommand.SELLSTOP, instrument, sellPrice, takeProfitPips, stopLossPips);
            }
        }

    }


    public IOrder submitOrder(OrderCommand orderCommand, Instrument instr, double price, double takeProfitPips, double stopLossPips) throws JFException {
        double takeProfitPrice = 0.0;
        double stopLossPrice = 0.0;

        // Calculating order price, stop loss and take profit prices
        if (orderCommand.isLong()) {
            if (takeProfitPips > 0) {
                takeProfitPrice = price + getPipPrice(takeProfitPips, instr);
            }
            if (stopLossPips > 0) {
                stopLossPrice = price - getPipPrice(stopLossPips, instr);
            }
        } else {
            if (takeProfitPips > 0) {
                takeProfitPrice = price - getPipPrice(takeProfitPips, instr);
            }
            if (stopLossPips > 0) {
                stopLossPrice = price + getPipPrice(stopLossPips, instr);
            }
        }

        print(stopLossPrice, takeProfitPrice);

        return engine.submitOrder(getLabel(instr), instr, orderCommand, amount, price, slippage, stopLossPrice, takeProfitPrice);
    }

    private void closeOrder(IOrder order) throws JFException {
        if(order == null) {
            return;
        }
        if(order.getState() == IOrder.State.CREATED) {
            order.waitForUpdate();
        }
        if (order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CANCELED) {
            order.close();
            order.waitForUpdate();
            if(order.getState() == IOrder.State.FILLED) {
                // order OPENNED -> order.close() -> order FILLED -> recieves message ORDER_ALREADY_FILLED -> resend order.close()
                order.close();
            }
        }
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != this.instrument) {
            return;
        }
    }

    private IOrder submitOrder(OrderCommand orderCmd, double price, double sl, double tp) throws JFException {
        return engine.submitOrder(getLabel(instrument), instrument, orderCmd, amount, price, slippage, sl, tp);
    }

    private boolean isActive(IOrder order) throws JFException {
        if (order != null &&
                order.getState() != IOrder.State.CLOSED &&
                order.getState() != IOrder.State.CREATED &&
                order.getState() != IOrder.State.CANCELED) {
            return true;
        }
        return false;
    }

    private double getPipPrice(double pips, Instrument instr) {
        return pips * instr.getPipValue();
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
        print(message);
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onStop() throws JFException {
    }

/************************************************/
/*  PRINT                                       */
    /************************************************/

    private void print(Object... o) {
        for (Object ob : o) {
            //console.getOut().print(ob + "  ");
            if (ob instanceof Double) {
                print2(toStr((Double) ob));
            } else if (ob instanceof double[]) {
                print((double[]) ob);
            } else if (ob instanceof double[][]) {
                print((double[][]) ob);
            } else if (Long.class.isInstance(ob)) {
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

    private void print2(Object o) {
        console.getOut().print(o);
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

    public String toStr(long time) {
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

    private void print(Long time) {
        console.getOut().println(toStr(time));
    }

    private void print(Throwable th) {
        StackTraceElement[] elem = th.getStackTrace();

        // print stack trace in reverse order because console in jforex client prints in reverse
        for(int i = elem.length - 1; i >= 0; i--) {
            console.getErr().println(elem[i]);
        }
    }
}
