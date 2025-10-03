package com.retailpulse.payment.infrastructure;

import com.retailpulse.payment.infrastructure.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        name = "salesClient",
        url = "${sales-service.url}",
        configuration = FeignConfig.class
)
public interface SalesFeignClient {

}
