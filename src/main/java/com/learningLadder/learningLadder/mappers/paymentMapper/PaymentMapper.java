package com.learningLadder.learningLadder.mappers.paymentMapper;

import com.learningLadder.learningLadder.dto.paymentDto.CheckoutCartRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.ConfirmManualPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CreateSalaryPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CreateSchoolFeePaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.PaymentResponseDto;
import com.learningLadder.learningLadder.dto.paymentDto.PaymentSummaryResponseDto;
import com.learningLadder.learningLadder.dto.paymentDto.RefundPaymentRequestDto;
import com.learningLadder.learningLadder.entity.payment.Payment;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;


@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface PaymentMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentType", constant = "COURSE_PURCHASE")
    @Mapping(target = "direction", constant = "INCOMING")
    @Mapping(target = "provider", source = "provider")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    Payment toEntity(CheckoutCartRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentType", constant = "SCHOOL_FEE")
    @Mapping(target = "direction", constant = "INCOMING")
    @Mapping(target = "academicYear", source = "academicYear")
    @Mapping(target = "term", source = "term")
    @Mapping(target = "feeCategory", source = "feeCategory")
    @Mapping(target = "subtotal", source = "amount")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "dueDate", source = "dueDate")
    @Mapping(target = "installmentNumber", source = "installmentNumber")
    @Mapping(target = "totalInstallments", source = "totalInstallments")
    @Mapping(target = "provider", source = "provider")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    @Mapping(target = "externalReference", source = "externalReference")
    @Mapping(target = "receiptNumber", source = "receiptNumber")
    @Mapping(target = "paidAt", source = "paidAt")
    @Mapping(target = "notes", source = "notes")
    Payment toEntity(CreateSchoolFeePaymentRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentType", constant = "STAFF_SALARY")
    @Mapping(target = "direction", constant = "OUTGOING")
    @Mapping(target = "provider", constant = "MANUAL")
    @Mapping(target = "payPeriodStart", source = "payPeriodStart")
    @Mapping(target = "payPeriodEnd", source = "payPeriodEnd")
    @Mapping(target = "grossAmount", source = "grossAmount")
    @Mapping(target = "allowancesAmount", source = "allowancesAmount")
    @Mapping(target = "deductionsAmount", source = "deductionsAmount")
    @Mapping(target = "subtotal", source = "grossAmount")
    @Mapping(target = "amount", expression = "java(dto.netAmount())")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "notes", source = "notes")
    Payment toEntity(CreateSalaryPaymentRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentType", constant = "REFUND")
    @Mapping(target = "direction", constant = "OUTGOING")
    @Mapping(target = "refundReason", source = "reason")
    Payment toRefundEntity(RefundPaymentRequestDto dto);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "externalReference", source = "externalReference")
    @Mapping(target = "receiptNumber", source = "receiptNumber")
    @Mapping(target = "paidAt", source = "paidAt")
    void applyManualConfirmation(ConfirmManualPaymentRequestDto dto, @MappingTarget Payment payment);

    @Mapping(target = "payerId", source = "payer.id")
    @Mapping(target = "payeeId", source = "payee.id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "shoppingCartId", source = "shoppingCart.id")
    @Mapping(target = "payslipDocumentId", source = "payslipDocument.id")
    @Mapping(target = "originalPaymentId", source = "originalPayment.id")
    @Mapping(target = "receiptDocumentId", source = "receiptDocument.id")
    PaymentResponseDto toStaffResponse(Payment payment);

    @Mapping(target = "payerId", source = "payer.id")
    @Mapping(target = "payeeId", source = "payee.id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "shoppingCartId", source = "shoppingCart.id")
    @Mapping(target = "payslipDocumentId", source = "payslipDocument.id")
    @Mapping(target = "originalPaymentId", source = "originalPayment.id")
    @Mapping(target = "receiptDocumentId", source = "receiptDocument.id")
    @Mapping(target = "providerFee", ignore = true)
    @Mapping(target = "netAmount", ignore = true)
    @Mapping(target = "providerStatus", ignore = true)
    @Mapping(target = "providerPaymentId", ignore = true)
    @Mapping(target = "providerCheckoutSessionId", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "notes", ignore = true)
    PaymentResponseDto toUserResponse(Payment payment);

    @Mapping(target = "payerId", source = "payer.id")
    @Mapping(target = "payeeId", source = "payee.id")
    @Mapping(target = "studentId", source = "student.id")
    PaymentSummaryResponseDto toSummary(Payment payment);

    List<PaymentSummaryResponseDto> toSummaryList(List<Payment> payments);
}
