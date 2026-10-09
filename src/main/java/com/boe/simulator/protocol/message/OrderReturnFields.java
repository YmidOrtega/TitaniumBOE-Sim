package com.boe.simulator.protocol.message;

import com.boe.simulator.server.order.Order;

import static com.boe.simulator.protocol.message.ReturnField.*;

/** Optional-field values an outbound message can echo for an order or an inbound request. */
public final class OrderReturnFields {

    private OrderReturnFields() {}

    public static ReturnFields forOrder(Order order) {
        ReturnFields fields = new ReturnFields().echo(order.getEchoFields());
        boolean booked = order.getState().isActive() && order.getLeavesQty() > 0 && order.getPrice() != null;
        fields.put(SIDE, order.getSide().wireValue())
                .put(PRICE, order.getPrice())
                .put(ORD_TYPE, order.getOrdType().wireValue())
                .put(TIME_IN_FORCE, order.getTimeInForce().wireValue())
                .put(SYMBOL, order.getSymbol())
                .put(CAPACITY, order.getCapacity() != null ? order.getCapacity().wireValue() : null)
                .put(ACCOUNT, order.getAccount())
                .put(CLEARING_FIRM, order.getClearingFirm())
                .put(CLEARING_ACCOUNT, order.getClearingAccount())
                .put(ORDER_QTY, order.getEffectiveOrderQty())
                .put(MATURITY_DATE, order.getMaturityDate())
                .put(STRIKE_PRICE, order.getStrikePrice())
                .put(PUT_OR_CALL, order.getPutOrCall() != null ? order.getPutOrCall().wireValue() : null)
                .put(OPEN_CLOSE, order.getOpenClose() != null ? order.getOpenClose().wireValue() : null)
                .put(LEAVES_QTY, order.getLeavesQty())
                .put(CUM_QTY, order.getCumQty())
                .put(AVG_PX, order.getAvgPx())
                .put(DAY_ORDER_QTY, order.getEffectiveOrderQty())
                .put(DAY_CUM_QTY, order.getCumQty())
                .put(DAY_AVG_PX, order.getAvgPx())
                .put(WORKING_PRICE, booked ? order.getPrice() : null)
                .put(DISPLAY_PRICE, booked ? order.getPrice() : null);
        if (fields.get(ROUTING_INST) == null && order.getRoutingInst() != null) {
            fields.put(ROUTING_INST, order.getRoutingInst().wireValue());
        }
        return fields;
    }

    public static ReturnFields forNewOrder(NewOrderMessage message) {
        return new ReturnFields().echo(message.getRawFields())
                .put(SIDE, message.getSide())
                .put(ORDER_QTY, message.getOrderQty());
    }
}
