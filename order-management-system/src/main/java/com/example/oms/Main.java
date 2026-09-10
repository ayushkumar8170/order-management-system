package com.example.oms;

import com.example.oms.config.DatabaseConfig;
import com.example.oms.dao.impl.CustomerDaoImpl;
import com.example.oms.dao.impl.OrderDaoImpl;
import com.example.oms.dao.impl.ProductDaoImpl;
import com.example.oms.dao.impl.TransactionDaoImpl;
import com.example.oms.exception.GlobalExceptionHandler;
import com.example.oms.exception.OmsException;
import com.example.oms.model.Product;
import com.example.oms.service.OrderService;
import com.example.oms.service.dto.OrderLineRequest;
import com.example.oms.service.dto.OrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

/**
 * Demo entry point: wires the DAO/service layers against the configured
 * MySQL instance and places one sample order end-to-end. This is meant to
 * be read as a usage example for OrderService as much as it is a runnable
 * demo — in a larger deployment this class would be replaced by a REST
 * controller layer that calls the same OrderService.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        DataSource dataSource = DatabaseConfig.getDataSource();

        var productDao = new ProductDaoImpl(dataSource);
        var customerDao = new CustomerDaoImpl(dataSource);
        var orderDao = new OrderDaoImpl(dataSource);
        var transactionDao = new TransactionDaoImpl();

        var orderService = new OrderService(dataSource, productDao, customerDao, orderDao, transactionDao);

        try {
            log.info("Current catalog:");
            for (Product p : productDao.findAll()) {
                log.info("  {}", p);
            }

            // Demo: customer #1 buys 2x product #1 and 1x product #2.
            // (Run sql/schema.sql + sql/seed.sql first so these ids exist.)
            List<OrderLineRequest> lines = List.of(
                    new OrderLineRequest(1, 2),
                    new OrderLineRequest(2, 1)
            );

            OrderResult result = orderService.placeOrder(1, lines);
            log.info("Placed order #{} - total {} - transaction {}",
                    result.order().getId(), result.order().getTotalAmount(),
                    result.transaction().getReferenceCode());

        } catch (OmsException e) {
            // Every failure path funnels through the same centralized handler.
            var handled = GlobalExceptionHandler.handle(e);
            log.error("Order placement failed [{}]: {} ({})",
                    handled.correlationId(), handled.message(), handled.errorCode());
        } finally {
            DatabaseConfig.close();
        }
    }
}
