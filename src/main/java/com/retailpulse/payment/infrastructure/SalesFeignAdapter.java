package com.retailpulse.payment.infrastructure;

import com.retailpulse.payment.domain.port.SalesPort;
import org.springframework.stereotype.Component;

@Component
public class SalesFeignAdapter implements SalesPort {

    private final SalesFeignClient client;

    public SalesFeignAdapter(SalesFeignClient client) {
        this.client = client;
    }


}
