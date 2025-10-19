package com.retailpulse.payment.infrastructure;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

 class SalesFeignAdapterTest {

    @Test
    void constructsWithClient() {
        SalesFeignClient client = mock(SalesFeignClient.class);
        new SalesFeignAdapter(client); // no exceptions is enough for now
    }

}
