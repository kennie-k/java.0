package com.kenyarealestate.payment.repository;

import com.kenyarealestate.payment.entity.MpesaRawCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MpesaRawCallbackRepository extends JpaRepository<MpesaRawCallback, UUID> {

    List<MpesaRawCallback> findByPaymentIdOrderByReceivedAtAsc(UUID paymentId);

    List<MpesaRawCallback> findByCheckoutRequestIdOrderByReceivedAtAsc(String checkoutRequestId);
}
