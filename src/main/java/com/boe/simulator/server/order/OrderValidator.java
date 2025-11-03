package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.NewOrderMessage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class OrderValidator {
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
        if (message.getSide() != 1 && message.getSide() != 2) errors.add("Invalid Side: must be 1 (Buy) or 2 (Sell)");

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
        byte ordType = message.getOrdType();
        if (ordType == 2) { // Limit order
            if (message.getPrice() == null) errors.add("Price is required for limit orders");
            else {
                String priceError = validatePrice(message.getPrice());
                if (priceError != null) errors.add(priceError);
            }
        }

        // 6. Validar Capacity (requerido)
        if (message.getCapacity() == 0 || message.getCapacity() == ' ') return ValidationResult.invalid("Capacity is required");
        else {
            String capacityError = validateCapacity(message.getCapacity());
            if (capacityError != null) errors.add(capacityError);
        }

        // 7. Validar OpenClose (si está presente)
        if (message.getOpenClose() != 0) {
            String openCloseError = validateOpenClose(message.getOpenClose());
            if (openCloseError != null) errors.add(openCloseError);
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
            if (message.getPutOrCall() != '0' && message.getPutOrCall() != '1') errors.add("Invalid PutOrCall: must be 0 (Put) or 1 (Call)");
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

    private String validateCapacity(byte capacity) {
        // C=Customer, M=MarketMaker, F=Firm, U=ProfessionalCustomer,
        // N=AwayMarketMaker, B=BrokerDealer, J=JointBackOffice
        return switch (capacity) {
            case 'C', 'M', 'F', 'U', 'N', 'B', 'J' -> null;
            default -> "Invalid Capacity: " + (char)capacity;
        };
    }

    private String validateOpenClose(byte openClose) {
        // O=Open, C=Close, N=None
        return switch (openClose) {
            case 'O', 'C', 'N' -> null;
            default -> "Invalid OpenClose: " + (char)openClose;
        };
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

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}