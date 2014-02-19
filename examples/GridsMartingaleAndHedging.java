package jforex.strategies.indicators;

import com.dukascopy.api.feed.IPointAndFigure;
import com.dukascopy.api.feed.IRangeBar;
import com.dukascopy.api.feed.IRenkoBar;
import com.dukascopy.api.feed.ITickBar;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.feed.FeedDescriptor;
import com.dukascopy.api.feed.IBarFeedListener;
import com.dukascopy.api.feed.IPointAndFigureFeedListener;
import com.dukascopy.api.feed.IPriceAggregationBar;
import com.dukascopy.api.feed.IRangeBarFeedListener;
import com.dukascopy.api.feed.IRenkoBarFeedListener;
import com.dukascopy.api.feed.ITickBarFeedListener;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IndicatorInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class GridsMartingaleAndHedging implements IStrategy, IPriceAggregationBar, IRenkoBarFeedListener, IRangeBarFeedListener, IPointAndFigureFeedListener, ITickBarFeedListener, IBarFeedListener {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private IContext context;
    private int counter = 0;
    private IAccount account;
    
    
    @Configurable("Ris % from acount equity")
    public double risk = 30;
    @Configurable("Amount")
    public double amount = 0.02;
    @Configurable("Amount increase multiplier")
    public double amountMult = 1.4;
    @Configurable("Pip stop")
    public double pipStop = 40;
    @Configurable("Take profit pips")
    public int takeTotalProfitPips = 40;
    
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    
    
    @Configurable("Offer side")
    public OfferSide offerSide = OfferSide.BID;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Filter")
    public Filter filter = Filter.ALL_FLATS;
    @Configurable("Applied price")
    public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
    
    @Configurable("Stop loss in pips")
    public int stopLossPips = 0;
    
    
    @Configurable("Max long orders")
    public int maxLongOrders = 10;
    @Configurable("Max short orders")
    public int maxShortOrders = 10;
    @Configurable("Max total orders")
    public int maxTotalOrders = 20;
    
    
    @Configurable("Chart type")
    public DataType chartType = DataType.TIME_PERIOD_AGGREGATION;
    
    @Configurable("Period")
    public Period selectedPeriod = Period.TEN_MINS;
    @Configurable("Use custom time period")
    public boolean useCustomPeriod = false;
    @Configurable("Tick bar size (for TIME_PERIOD_AGGREGATION charts)")
    public Unit unit = Unit.Minute;
    @Configurable("Tick bar size (for TIME_PERIOD_AGGREGATION charts)")
    public int unitsCount = 3;
    
    @Configurable("Price range in pips (for POINT_AND_FIGURE, RENKO, RANGE charts)")
    public int priceRange = 1;
    @Configurable("Reversal amount in pips (for POINT_AND_FIGURE charts)")
    public int reversalAmount = 3;
    @Configurable("Tick bar size (for TICK_BAR charts)")
    public int tickBarSize = 3;
    
    
    private OrderMgr orderMgr;
    private FeedDescriptor feedDescr;
    
    private double buyAmount;
    private double sellAmount;
    
    private MyOrder latestBuyOrder;
    private MyOrder latestSellOrder;
    
    
    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
        this.context = context;
        this.account = context.getAccount();
        
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(instrument);                        
        context.setSubscribedInstruments(instruments, true);
        
        if(useCustomPeriod) {
            selectedPeriod = Period.createCustomPeriod(unit, unitsCount);
        }
        
        feedDescr = configureChartType(context, instrument);
        
        orderMgr = new OrderMgr(maxLongOrders, maxShortOrders, maxTotalOrders);
        
        buyAmount = amount;
        latestBuyOrder = orderMgr.submitOrder(OrderCommand.BUY, instrument, history.getLastTick(instrument), buyAmount);
        
    }

    // this method is called from correct onBar method depending on the chart type that is subscribed
    // !!! use local method getBars instead of history.getBars
    // !!! use local method calculateIndicator instead of IIndicators methods
    private void onBar(ITick tick, IBar bar) throws JFException {
    }
    

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {

        if(!this.instrument.equals(instrument)) {
            return;
        }
        
        orderMgr.cleanClosedOrders();
        double maxLoss = account.getEquity() * risk / 100;
        
        
        double profitLossCurr = 0;
        for(MyOrder order : orderMgr.longOrders.values()) {
            profitLossCurr += order.order.getProfitLossInAccountCurrency();
        }
        
        if(profitLossCurr <= -maxLoss) {
            // hedge
            if(orderMgr.shortOrders.isEmpty()) {
                sellAmount = amount;
                latestSellOrder = orderMgr.submitOrder(OrderCommand.SELL, instrument, tick, sellAmount);
            }
            
        } else if(profitLossCurr >= takeTotalProfitPips) {
            // close all and place new order
            orderMgr.closeAllLongOdrers();
            buyAmount = amount;
            latestBuyOrder = orderMgr.submitOrder(OrderCommand.BUY, instrument, tick, buyAmount);
        }
        
        if(latestBuyOrder != null && latestBuyOrder.isActive() && latestBuyOrder.order.getProfitLossInPips() <= -pipStop) {
            // pip stop
            buyAmount = buyAmount * amountMult;
            latestBuyOrder = orderMgr.submitOrder(OrderCommand.BUY, instrument, tick, buyAmount);
        }
        
        
        profitLossCurr = 0;
        for(MyOrder order : orderMgr.shortOrders.values()) {
            profitLossCurr += order.order.getProfitLossInAccountCurrency();
        }
        
        if(profitLossCurr <= -maxLoss) {
            // hedge
            if(orderMgr.longOrders.isEmpty()) {                
                buyAmount = amount;
                latestBuyOrder = orderMgr.submitOrder(OrderCommand.BUY, instrument, tick, buyAmount);
            }
            
        } else if(profitLossCurr >= takeTotalProfitPips) {
            // close all and place new order
            orderMgr.closeAllShortOdrers();
            sellAmount = amount;
            latestSellOrder = orderMgr.submitOrder(OrderCommand.SELL, instrument, tick, sellAmount);
        }
        
        if(latestSellOrder != null && latestSellOrder.isActive() && latestSellOrder.order.getProfitLossInPips() <= -pipStop) {
            // pip stop
            sellAmount = sellAmount * amountMult;
            latestSellOrder = orderMgr.submitOrder(OrderCommand.SELL, instrument, tick, sellAmount);
        }
    }
    
    @Override
    public void onMessage(IMessage message) throws JFException {
    }

    @Override
    public void onAccount(IAccount account) throws JFException {
    }

    @Override
    public void onStop() throws JFException {
    }
    
    
    
/************************************************/
/*  ORDER MANAGER                               */
/************************************************/
    
    private class OrderMgr {

        private int maxLongOrdCount = 1;
        private int maxShortOrdCount = 1;
        private int maxOrdCount = 1;
        private Map<String, MyOrder> longOrders = new HashMap<String, MyOrder>();
        private Map<String, MyOrder> shortOrders = new HashMap<String, MyOrder>();

        OrderMgr(int maxLongOrders, int maxShortOrders, int maxTotalOrders) {
            this.maxLongOrdCount = maxLongOrders;
            this.maxShortOrdCount = maxShortOrders;
            this.maxOrdCount = maxTotalOrders;
        }

        OrderMgr(int maxTotalOrders) {
            this.maxOrdCount = maxTotalOrders;
        }

        OrderMgr() {
        }

        private MyOrder addOrder(IOrder order) throws JFException {

            MyOrder myOrder = new MyOrder(order);
            
            if (order.isLong()) {
                longOrders.put(order.getLabel(), myOrder);
                
            } else {
                shortOrders.put(order.getLabel(), myOrder);

            }
            
            return myOrder;
        }

        public boolean canAddOrder(OrderCommand ordCmd) {
            // do nothing if max order count reached
            if (longOrders.size() + shortOrders.size() >= maxOrdCount) {
                return false;
            }
            if (ordCmd.isLong()) {
                if (longOrders.size() >= maxLongOrdCount) {
                    return false;
                }
            } else {
                if (shortOrders.size() >= maxShortOrdCount) {
                    return false;
                }
            }
            return true;
        }

        // remove all orders that are in state CLOSED or CANCELLED
        // discandrs orders closed by user, take profit or stop loss
        public void cleanClosedOrders() throws JFException {
            cleanClosedOrders(longOrders);
            cleanClosedOrders(shortOrders);
        }

        public void closeAll() throws JFException {
            closeAllLongOdrers();
            closeAllShortOdrers();
        }

        public void closeAllLongOdrers() throws JFException {
            closeAll(longOrders);
        }

        public void closeAllShortOdrers() throws JFException {
            closeAll(shortOrders);
        }

        private void cleanClosedOrders(Map<String, MyOrder> orders) throws JFException {
            List<String> remove = new ArrayList<String>();
            for (String label : orders.keySet()) {
                MyOrder order = orders.get(label);
                if (!order.isActive()) {
                    order.close();
                    remove.add(label);
                }
            }
            for (String label : remove) {
                orders.remove(label);
            }
        }

        private void closeAll(Map<String, MyOrder> orders) throws JFException {
            for (String label : orders.keySet()) {
                MyOrder order = orders.get(label);
                order.close();
            }
            orders.clear();
        }

        public void updateTrailingStopLoss(ITick tick, double pTriggerPips, double pStopLossPips) throws JFException {

            if (pStopLossPips > 0) {
                for (MyOrder order : longOrders.values()) {
                    order.updateTrailingStopLoss(tick, pTriggerPips, pStopLossPips);
                }
                for (MyOrder order : shortOrders.values()) {
                    order.updateTrailingStopLoss(tick, pTriggerPips, pStopLossPips);
                }

            }
        }

        public int getLongOrderCount() {
            return longOrders.size();
        }

        public int getShortOrderCount() {
            return shortOrders.size();
        }

        public Collection<MyOrder> getLongOders() {
            return longOrders.values();
        }

        public Collection<MyOrder> getShortOders() {
            return shortOrders.values();
        }

        public MyOrder submitOrder(OrderCommand orderCommand, Instrument instr, ITick t, double pAmount) throws JFException {

            if(!canAddOrder(orderCommand)) {
                return null;
            }
            
            double stopLossPrice = 0.0;


            // Calculating order price, stop loss and take profit prices
            if (orderCommand.isLong()) {
                if (stopLossPips > 0) {
                    stopLossPrice = t.getBid() - pip(stopLossPips, instr);
                }
            } else {
                if (stopLossPips > 0) {
                    stopLossPrice = t.getAsk() + pip(stopLossPips, instr);
                }
            }

            return submitOrder(orderCommand, instr, t, pAmount, stopLossPrice);
        }

        public MyOrder submitOrder(OrderCommand orderCommand, Instrument instr, ITick t, double pAmount, double stopLossPrice) throws JFException {
            
            if(!canAddOrder(orderCommand)) {
                return null;
            }

            double takeProfitPrice = 0.0;
//
//            // Calculating order price, stop loss and take profit prices
//            if (orderCommand.isLong()) {
//                if (takeTotalProfitPips > 0) {
//                    takeProfitPrice = t.getBid() + pip(takeTotalProfitPips, instr);
//                }
//            } else {
//                if (takeTotalProfitPips > 0) {
//                    takeProfitPrice = t.getAsk() - pip(takeTotalProfitPips, instr);
//                }
//            }

            IOrder order = engine.submitOrder(getLabel(instr), instr, orderCommand, pAmount, 0, slippage, stopLossPrice, takeProfitPrice);
            
            MyOrder myOrder = addOrder(order);
            return myOrder;
        }
    }
    
    
/************************************************/
/*  ORDER                                       */
/************************************************/

    private class MyOrder {

        private IOrder order;
        private Map<String, Object> properties;

        MyOrder(IOrder order) {
            this.order = order;
        }

        void setProperty(String key, Object value) {
            if (properties == null) {
                properties = new HashMap();
            }
            properties.put(key, value);
        }

        Object getProperty(String key) {
            return properties.get(key);
        }

        void close() throws JFException {
            if (order == null) {
                return;
            }
            if (order.getState() == IOrder.State.CREATED) {
                order.waitForUpdate();
            }
            if (order.getState() == IOrder.State.OPENED) {
                order.close(); // close 1
                order.waitForUpdate();
            }
            if (order.getState() == IOrder.State.FILLED) {
                // order in state OPENNED -> close 1 -> order FILLED before CLOSED -> recieves message ORDER_ALREADY_FILLED -> close 2
                order.close(); // close 2
                order = null;
            }          
        }

        boolean isActive() throws JFException {
            if (order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CANCELED) {
                return true;
            }
            return false;
        }

        boolean inState(IOrder.State state) {
            return order.getState() == state;
        }

        public void updateTrailingStopLoss(ITick tick, double pTriggerPips, double pStopLossPips) throws JFException {

            if (order != null && inState(IOrder.State.FILLED)) {

                Instrument instr = order.getInstrument();

                double newStop;
                double openPrice = order.getOpenPrice();
                double currentStopLoss = order.getStopLossPrice();

                // (START) trailing stop loss is activated when price is higher than oper price + trailingTrigger pips
                // (TRAILING STOP) if price moves further up (for BUY order), stop loss is updated to stopLossPips

                if (order.isLong()) { // long side order                
                    if ((currentStopLoss == 0.0 || tick.getBid() > currentStopLoss + pip(pStopLossPips, instr))
                            && tick.getBid() > openPrice + pip(pTriggerPips, instr)) {
                        // trailing stop loss
                        newStop = tick.getBid() - pip(pStopLossPips, instr);
                        newStop = round(newStop, instr);

                        if (currentStopLoss != newStop) {
                            order.setStopLossPrice(newStop);
                            return;
                        }
                    }

                } else { // short side order            
                    if ((currentStopLoss == 0.0 || tick.getAsk() < currentStopLoss - pip(pStopLossPips, instr))
                            && tick.getAsk() < openPrice - pip(pTriggerPips, instr)) {

                        // trailing stop loss
                        newStop = tick.getAsk() + pip(pStopLossPips, instr);
                        newStop = round(newStop, instr);

                        if (currentStopLoss != newStop) {
                            order.setStopLossPrice(newStop);
                            return;
                        }
                    }
                }
            }
        }
        
        public void setStopLossPips(double currentPrice,  double pips) throws JFException {
            double newStopLoss = currentPrice + pip(pips, order.getInstrument());
            newStopLoss = round(newStopLoss, order.getInstrument());
            if(newStopLoss != order.getStopLossPrice()) {
                order.setStopLossPrice(newStopLoss);
            }
            
        }
    }
    
/************************************************/
/*  INTEGRATE WITH DIFFERENT CHART TYPES        */
/************************************************/
    
    @Override
    public void onBar(Instrument instrument, Period period, OfferSide offerSide, IBar bar) {
        try {
            if (!period.equals(this.selectedPeriod) || !this.instrument.equals(instrument)) {
                return;
            }
            ITick tick = history.getLastTick(instrument);
            onBar(tick, bar);
        } catch (JFException ex) {
            print(ex);
        }
    }
    
    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!period.equals(this.selectedPeriod) || !this.instrument.equals(instrument)) {
            return;
        }
        ITick tick = history.getLastTick(instrument);
        IBar bar = offerSide.equals(offerSide.ASK) ? askBar : bidBar;
        onBar(tick, bar);
    }    

    @Override
    public void onBar(Instrument instrument, OfferSide offerSide, PriceRange brickSize, IRenkoBar bar) {
        // assuming we are subscribed only to correct instrument and price range
        try {
            if (!this.instrument.equals(instrument)) {
                return;
            }
            ITick tick = history.getLastTick(instrument);
            onBar(tick, bar);
        } catch (JFException ex) { 
            print(ex);
        }
    }

    @Override
    public void onBar(Instrument instrument, OfferSide offerSide, PriceRange priceRange, IRangeBar bar) {
        // assuming we are subscribed only to correct instrument and price range
        if (!this.instrument.equals(instrument)) {
            return;
        }
        try {
            ITick tick = history.getLastTick(instrument);
            onBar(tick, bar);
        } catch (JFException ex) { 
            print(ex);
        }
    }

    @Override
    public void onBar(Instrument instrument, OfferSide offerSide, PriceRange priceRange, ReversalAmount reversalAmount, IPointAndFigure bar) {
        // assuming we are subscribed only to correct instrument and price range
        if (!this.instrument.equals(instrument)) {
            return;
        }
        try {
            ITick tick = history.getLastTick(instrument);
            onBar(tick, bar);
        } catch (JFException ex) { 
            print(ex);
        }
    }

    @Override
    public void onBar(Instrument instrument, OfferSide offerSide, TickBarSize tickBarSize, ITickBar bar) {
        // assuming we are subscribed only to correct instrument and price range
        if (!this.instrument.equals(instrument)) {
            return;
        }
        try {
            ITick tick = history.getLastTick(instrument);
            onBar(tick, bar);
        } catch (JFException ex) { 
            print(ex);
        }
    }
    
    private IBar getBar(FeedDescriptor feedDescr, DataType dataType, int shift) throws JFException {
        switch (dataType) {            
            case POINT_AND_FIGURE:                
                return history.getPointAndFigure(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), feedDescr.getReversalAmount(), shift);
            
            case RENKO:
                return history.getRenkoBar(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), shift);
            
            case PRICE_RANGE_AGGREGATION:  
                return history.getRangeBar(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), shift);
                
            case TICK_BAR:                
                return history.getTickBar(feedDescr.getInstrument(), offerSide, feedDescr.getTickBarSize(), shift);
                
            default:
                long time = history.getLastTick(feedDescr.getInstrument()).getTime();
                time = history.getBarStart(selectedPeriod, time);
                return history.getBars(feedDescr.getInstrument(), selectedPeriod, offerSide, filter, 1, time, 0).get(0);
        }
    }
    
    private List getBars(FeedDescriptor feedDescr, DataType dataType, int barsBefore, long time, int barsAfter) throws JFException {
        switch (dataType) {
            // default Bar charts
            case POINT_AND_FIGURE:                
                return history.getPointAndFigures(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), feedDescr.getReversalAmount(), barsBefore, time, barsAfter);
            
            case RENKO:
                return history.getRenkoBars(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), barsBefore, time, barsAfter);
            
            case PRICE_RANGE_AGGREGATION:  
                return history.getRangeBars(feedDescr.getInstrument(), offerSide, feedDescr.getPriceRange(), barsBefore, time, barsAfter);
                
            case TICK_BAR:                
                return history.getTickBars(feedDescr.getInstrument(), offerSide, feedDescr.getTickBarSize(), barsBefore, time, barsAfter);
                
            default:
                return history.getBars(feedDescr.getInstrument(), selectedPeriod, offerSide, filter, barsBefore, time, barsAfter);
        }
    }
    
    private FeedDescriptor configureChartType(IContext context, Instrument instr) {
        
        FeedDescriptor feedDescr = null;
        
        switch (chartType) {
            // default Bar charts
            
            case TICKS:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setFilter(filter);
                feedDescr.setPeriod(selectedPeriod);
                break;
                
            case TIME_PERIOD_AGGREGATION:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setFilter(filter);
                feedDescr.setPeriod(selectedPeriod);
                
                if(useCustomPeriod) {
                    context.subscribeToBarsFeed(instr, selectedPeriod, offerSide, this);
                }
                break;
            
            case POINT_AND_FIGURE:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setPriceRange(PriceRange.valueOf(priceRange));
                feedDescr.setReversalAmount(ReversalAmount.valueOf(reversalAmount));
                feedDescr.setFilter(filter);
                
                context.subscribeToPointAndFigureFeed(instr, offerSide, PriceRange.valueOf(priceRange), ReversalAmount.valueOf(reversalAmount), this);
                break;
            
            case RENKO:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setPriceRange(PriceRange.valueOf(priceRange));
                feedDescr.setFilter(filter);
                
                context.subscribeToRenkoBarFeed(instr, offerSide, PriceRange.valueOf(priceRange), this);
                break;
            
            
            case PRICE_RANGE_AGGREGATION:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setPriceRange(PriceRange.valueOf(priceRange));
                feedDescr.setFilter(filter);
                context.subscribeToRangeBarFeed(instr, offerSide, PriceRange.valueOf(priceRange), this);
                
            case TICK_BAR:
                feedDescr = new FeedDescriptor();
                feedDescr.setDataType(chartType);
                feedDescr.setOfferSide(offerSide);
                feedDescr.setInstrument(instr);
                feedDescr.setFilter(filter);
                
                context.subscribeToTickBarFeed(instr, offerSide, TickBarSize.valueOf(tickBarSize), this);
                break;
        }
        
        return feedDescr;
    }
    
    
    
    Map<String, IndicatorInfo> infos = new HashMap<String, IndicatorInfo> ();
    
    
    // time - start time of a bar
    private double[] calculateIndicatorDouble(String indicatorName, FeedDescriptor feedDescr, DataType dataType, AppliedPrice appliedPrice, Object[] optInputs, int shift) throws JFException {
        IBar bar = getBar(feedDescr, dataType, 0);
        double[][] result = calculateIndicatorDouble(indicatorName, feedDescr, appliedPrice, optInputs, shift + 1, bar.getTime(), 0);
        return result[0];
    }
    
    private double[][] calculateIndicatorDouble(String indicatorName, FeedDescriptor feedDescr, AppliedPrice appliedPrice, Object[] optInputs, int candlesBefore, long time, int candlesAfter) throws JFException {
        OfferSide[] offerSides = new OfferSide[optInputs.length];
        AppliedPrice[] appliedPrices = new AppliedPrice[optInputs.length];
        
        for(int i = 0; i < optInputs.length; i++) {
            offerSides[i] = feedDescr.getOfferSide();
            appliedPrices[i] = appliedPrice;
        }
        
                
        IndicatorInfo info = infos.get(indicatorName);
        if(info == null) {
            info = indicators.getIndicator(indicatorName).getIndicatorInfo();
            infos.put(indicatorName, info);
        }
        
        int additionlBarCount = 0;
        int minBarCount = 1000;
        if(info.isRecalculateAll() || info.isUnstablePeriod()) {
            additionlBarCount = minBarCount - (candlesBefore + candlesAfter);
            if(additionlBarCount < 0) {
                additionlBarCount = 0;
            }
        }
                
        Object[] result = indicators.calculateIndicator(feedDescr, offerSides, indicatorName, appliedPrices, optInputs, additionlBarCount + candlesBefore, time, candlesAfter);
        double[][] newResult = new double[candlesBefore + candlesAfter][result.length];
        
        for(int i = 0; i < result.length; i++) {
            double[] values = (double[])result[i];
            
            for(int j = 0; j < candlesBefore + candlesAfter; j++) {
                newResult[j][i] = values[j + additionlBarCount];
            }
        }

        return newResult;
    }
    
    // time - start time of a bar
    private Object[] calculateIndicator(String indicatorName, FeedDescriptor feedDescr, DataType dataType, AppliedPrice appliedPrice, Object[] optInputs, int shift) throws JFException {
        IIndicator ind = indicators.getIndicator(indicatorName);
        
        IBar bar = getBar(feedDescr, dataType, 0);
        Object[] array = calculateIndicator(indicatorName, feedDescr, appliedPrice, optInputs, shift + 1, bar.getTime(), 0);
        Object[] result = new Object[array.length];
        
        for(int i = 0; i < result.length; i++) {
            switch(ind.getOutputParameterInfo(i).getType()) {
                case DOUBLE:
                    result[i] = ((double []) array[i])[0];
                    break;
                case INT:
                    result[i] = ((int []) array[i])[0];
                    break;
                case OBJECT:
                    result[i] = ((Object []) array[i])[0];
                    break;
            }
        }
        return result;
    }
    
    private Object[] calculateIndicator(String indicatorName, FeedDescriptor feedDescr, AppliedPrice appliedPrice, Object[] optInputs, int candlesBefore, long time, int candlesAfter) throws JFException {
        OfferSide[] offerSides = new OfferSide[optInputs.length];
        AppliedPrice[] appliedPrices = new AppliedPrice[optInputs.length];
        
        for(int i = 0; i < optInputs.length; i++) {
            offerSides[i] = feedDescr.getOfferSide();
            appliedPrices[i] = appliedPrice;
        }
        
                
        IndicatorInfo info = indicators.getIndicator(indicatorName).getIndicatorInfo();
        
        int additionlBarCount = 0;
        int minBarCount = 1000;
        if(info.isRecalculateAll() || info.isUnstablePeriod()) {
            additionlBarCount = minBarCount - (candlesBefore + candlesAfter);
            if(additionlBarCount < 0) {
                additionlBarCount = 0;
            }
        }
                
        return indicators.calculateIndicator(feedDescr, offerSides, indicatorName, appliedPrices, optInputs, additionlBarCount + candlesBefore, time, candlesAfter);
    }
    
    @Override
    public long getEndTime() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getFormedElementsCount() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getTime() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getOpen() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getClose() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getLow() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getHigh() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getVolume() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
/************************************************/
/*  HELPERS                                     */
/************************************************/

    private double pip(double pips, Instrument instr) {
        return pips * instr.getPipValue();
    }

    private String getLabel(Instrument instr) {
        String label = instr.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    // return true if time is in from-to interval of the day
    public boolean checkTimeInterval(long time, int fromHour, int fromMin, int toHour, int toMin) {
        Calendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, fromHour);
        cal.set(Calendar.MINUTE, fromMin);

        Calendar cal2 = new GregorianCalendar();
        cal2.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal2.setTimeInMillis(time);
        cal2.set(Calendar.HOUR_OF_DAY, toHour);
        cal2.set(Calendar.MINUTE, toMin);

        if (cal.getTimeInMillis() <= time &&
                time <= cal2.getTimeInMillis()) {
            return true;
        }
        return false;
    }

    private double round(double price, Instrument instr) {
        BigDecimal bd = new BigDecimal(price);
        bd = bd.setScale(instr.getPipScale() + 1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private double roundPips(double pips) {
        BigDecimal bd = new BigDecimal(pips);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.doubleValue();
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
            } else if (ob instanceof double[]) {
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
