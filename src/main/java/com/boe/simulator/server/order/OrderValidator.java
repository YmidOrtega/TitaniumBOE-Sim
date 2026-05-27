package com.boe.simulator.server.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.types.Capacity;
import com.boe.simulator.protocol.types.OpenClose;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.PutOrCall;
import com.boe.simulator.protocol.types.Side;

public class OrderValidator {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logger.getLogger(OrderValidator.class.getName());

    // Límites del sistema
    private static final int MAX_ORDER_QTY = 999999;
    private static final int MIN_ORDER_QTY = 1;

    // Patrones para validación
    private static final Pattern CLORDID_PATTERN = Pattern.compile("^[\\x21-\\x7E&&[^,;|@\"]]+$");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,8}$");

    public ValidationResult validateNewOrder(NewOrderMessage message) {
        List<String> errors = new ArrayList<>();

        // 1. Validar ClOrdID
        String clOrdIDError = validateClOrdID(message.getClOrdID());
        if (clOrdIDError != null) errors.add(clOrdIDError);

        // 2. Validar Side
        try { Side.fromByte(message.getSide()); }
        catch (IllegalArgumentException e) { errors.add("Invalid Side: " + e.getMessage()); }

        // 3. Validar OrderQty
        String qtyError = validateOrderQty(message.getOrderQty());
        if (qtyError != null) errors.add(qtyError);

        // 4. Validar Symbol (requerido)
        if (message.getSymbol() == null || message.getSymbol().isEmpty()) errors.add("Symbol is required");
        else {
            String symbolError = validateSymbol(message.getSymbol());
            if (symbolError != null) errors.add(symbolError);
        }

        // 5. Validar Price (requerido para limit orders)
        OrdType ordType = message.getOrdType() != 0 ? OrdType.fromByte(message.getOrdType()) : OrdType.LIMIT;
        if (ordType == OrdType.LIMIT) {
            if (message.getPrice() == null) errors.add("Price is required for limit orders");
            else {
                String priceError = validatePrice(message.getPrice());
                if (priceError != null) errors.add(priceError);
            }
        }

        // 6. Validar Capacity (requerido)
        if (message.getCapacity() == 0 || message.getCapacity() == ' ') {
            return ValidationResult.invalid("Capacity is required");
        } else {
            try { Capacity.fromByte(message.getCapacity()); }
            catch (IllegalArgumentException e) { errors.add("Invalid Capacity: " + e.getMessage()); }
        }

        // 7. Validar OpenClose (si está presente)
        if (message.getOpenClose() != 0) {
            try { OpenClose.fromByte(message.getOpenClose()); }
            catch (IllegalArgumentException e) { errors.add("Invalid OpenClose: " + e.getMessage()); }
        }

        // 8. Validar symbology completa (si es option)
        if (message.getMaturityDate() != null || message.getStrikePrice() != null) {
            String symbologyError = validateOptionSymbology(
                    message.getMaturityDate() != null,
                    message.getStrikePrice() != null,
                    message.getPutOrCall() != 0
            );
            if (symbologyError != null) errors.add(symbologyError);
        }

        // 9. Validar StrikePrice (si está presente)
        if (message.getStrikePrice() != null) {
            String strikePriceError = validatePrice(message.getStrikePrice());
            if (strikePriceError != null) errors.add("Invalid StrikePrice: " + strikePriceError);
        }

        // 10. Validar PutOrCall (si está presente)
        if (message.getPutOrCall() != 0) {
            try { PutOrCall.fromByte(message.getPutOrCall()); }
            catch (IllegalArgumentException e) { errors.add("Invalid PutOrCall: " + e.getMessage()); }
        }

        if (errors.isEmpty()) return ValidationResult.valid();
        else return ValidationResult.invalid(String.join("; ", errors));

    }

    private String validateClOrdID(String clOrdID) {
        if (clOrdID == null || clOrdID.isEmpty()) return "ClOrdID cannot be empty";

        if (clOrdID.length() > 20) return "ClOrdID exceeds maximum length of 20 characters";

        // Validar caracteres permitidos (ASCII 33-126 excepto , ; | @ ")
        if (!CLORDID_PATTERN.matcher(clOrdID).matches()) return "ClOrdID contains invalid characters";

        return null;
    }

    private String validateOrderQty(int orderQty) {
        if (orderQty < MIN_ORDER_QTY) return "OrderQty must be at least " + MIN_ORDER_QTY;

        if (orderQty > MAX_ORDER_QTY) return "OrderQty exceeds system limit of " + MAX_ORDER_QTY;

        return null;
    }

    private String validatePrice(BigDecimal price) {
        if (price == null) return "Price cannot be null";

        if (price.compareTo(BigDecimal.ZERO) < 0) return "Price cannot be negative";

        // Validar que no exceda límites razonables
        if (price.compareTo(new BigDecimal("999999.9999")) > 0) return "Price exceeds maximum value";


        return null;
    }

    private String validateSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) return "Symbol cannot be empty";

        if (symbol.length() > 8) return "Symbol exceeds maximum length of 8 characters";

        if (!SYMBOL_PATTERN.matcher(symbol).matches()) return "Symbol must contain only uppercase letters and numbers";

        return null;
    }

    private String validateOptionSymbology(boolean hasMaturity, boolean hasStrike, boolean hasPutCall) {
        // Si alguno está presente, todos deben estar presentes
        if (hasMaturity || hasStrike || hasPutCall) {
            if (!hasMaturity) return "MaturityDate is required for option orders";

            if (!hasStrike) return "StrikePrice is required for option orders";

            if (!hasPutCall) return "PutOrCall is required for option orders";
        }

        return null;
    }

    public boolean isDuplicateClOrdID(String clOrdID, OrderRepository repository) {
        return repository.existsByClOrdID(clOrdID);
    }

    public record ValidationResult(boolean isValid, String errorMessage) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        @Override
        public String toString() {
            return isValid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}