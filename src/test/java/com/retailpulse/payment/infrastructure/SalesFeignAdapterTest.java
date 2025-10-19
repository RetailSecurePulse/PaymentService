package com.retailpulse.payment.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SalesFeignAdapterTest {

    @Test
    void constructsWithClient() {
        SalesFeignClient client = mock(SalesFeignClient.class);
        SalesFeignAdapter adapter = new SalesFeignAdapter(client);

        assertThat(adapter).isNotNull();
        verifyNoInteractions(client);
    }

}
