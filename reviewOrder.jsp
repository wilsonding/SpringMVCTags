<%@ page language="java" contentType="text/html; charset=ISO-8859-1" pageEncoding="ISO-8859-1"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="tiles" uri="http://tiles.apache.org/tags-tiles" %>
<c:set value="${invoice.amount}" var="oneTimeAmount" />
<sec:authentication property='principal.currentAccount' var="currentAccount" />
<sec:authentication property='principal' var="principal" />
<script type='text/javascript' src="<c:url value='/js/shop/reviewOrder.js'/>"></script>

<!-- START CONTENT -->
<form:form id="reviewOrderForm" modelAttribute="order" action="${flowExecutionUrl}">

  <div class="content height800">
      <c:if test="${not empty order.submitOrderError}">
          <div class="albox errorbox">
              <p>There was an issue processing the order: </p>
              <p>${order.submitOrderError}</p>
              <p>Bookmark this page to try again later</p>
          </div>
      </c:if>
    <!-- Start review cart -->
    <div class="simplebox">
      <div class="titleh">
        <h3>Changes to your Account</h3>
      </div>
      <table class="reviewTable tablesorter"> 
        <thead>
          <tr>
				<th>Category/Description</th>
				<th>Quantity</th>
				<th>Unit Price</th>
				<th>Monthly</th>
				<th>One Time Fee</th>
			</tr>
	   </thead>
	   <tbody>
			<tiles:insertAttribute name="orderDetails" flush="true" />
       </tbody>
          <tfoot>
          <tr>
              <th align="right" colspan="3" style="font-size:16px;"><b>Total Due Today</b></th>
              <th align="right"></th>
              <th align="right"><b style="padding-right:12px"><fmt:formatNumber type="currency" value="${oneTimeAmount}" /></b></th>
          </tr>
          <c:if test="${order.shopCart.totalMonthlyFee > 0}">
              <tr>
                  <th align="right" colspan="4" style="color:#999;"><em>Monthly service fees will increase on your next statement by:  <b> <fmt:formatNumber
						  type="currency" value="${order.shopCart.totalMonthlyFee}" /> + taxes and fees </b></em></th>
                  <th align="right"><b>&nbsp;</b></th>
              </tr>
          </c:if>
          </tfoot>
      </table>
      <div class="clear margin-bottom20"></div>
    </div>
    <!-- End review cart -->

    <div class="grid270-left padding-top20">
      <h2 class="blue font14 non-bold padding-bottom10">Account Information <button onclick="location.href='/adminv2/account/${order.accountId}/settings/companycontact'; return false;" class="button-gray" style="float:none;display:none;">Edit</button></h2>
      ${currentAccount.accountName}<br/>
      ${currentAccount.contactName}<br/>
      ${currentAccount.contactPhone}<br/>
      ${currentAccount.address1}<br/>
      <c:if test="${currentAccount.address2 and fn:length(currentAccount.address2) gt 0}">
      ${currentAccount.address2}<br/>
      </c:if>
      ${currentAccount.city}, ${currentAccount.provinceName} ${currentAccount.zip}<br/>
      ${currentAccount.countryName}<br/>
      ${currentAccount.contactEmail}
    </div>

	<div id="leftDiv" class="grid-left padding-top20">
    <div >
      <h2 class="blue font14 non-bold padding-bottom10">Account Payment Method<button onclick="location.href='/adminv2/account/${order.accountId}/billing-new/credit-card'; return false;" class="button-gray" style="float:none;display:none;">Edit</button></h2>
      <!-- Paying with: ${order.paymentMethodList[0].paymentMethodId}
        <c:if test="${order.paymentMethodList.size() == 1}">
            ${accountPayment.externalBillingDefaultPaymentMethod.type}<br/>
        </c:if>

        <c:if test="${order.paymentMethodList.size() > 1}">
            <select id="paymentMethods" class="uniform">
                <c:forEach items="${order.paymentMethodList}" var="paymentMethod">
                    <option>${paymentMethod.type}</option>
                </c:forEach>
            </select>
        </c:if> -->

        Paying with: 
        <c:if test="$paymentMethodList.size() == 1}">
            ${accountPayment.externalBillingDefaultPaymentMethod.type}<br/>
        </c:if>

        <c:if test="${paymentMethodList.size() > 1}">
            <select path="paymentMethodId" id="paymentMethods" class="uniform">
                <c:forEach items="${paymentMethodList}" var="paymentMethod">
                    <option value="${paymentMethod.paymentMethodId}">${paymentMethod.type}</option>
                </c:forEach>
            </select>
        </c:if>

      ${accountPayment.billingContactName}<br/>
      ${accountPayment.externalBillingDefaultPaymentMethod.creditCardMaskNumber}<br/>
    </div>

	<c:if test="${principal.isVocalocitySuperUser()}">
		<div id="dateBookedDiv">
			<h2 class="blue font14 non-bold padding-bottom10">Date Booked</h2>
			<form:input path="dateBooked" cssClass="datepicker-input" id="dateBooked"/>
		</div>
	</c:if>
	</div>

    <c:if test="${order.shippingNeeded}">
    <c:forEach items="${order.shipping.locations}" var="location">
    <c:if test="${fn:length(location.nonZeroQuantityHardwares) gt 0}">
    <div class="clear divider"></div>
    <div class="grid270-left padding-top20">
          <h2 class="blue font14 non-bold padding-bottom10">Shipping Address <button name="_eventId_back" class="button-gray" style="float:none;">Edit</button></h2>
          ${location.name}<br/>
          ${location.address.address1}<br/>
          <c:if test="${location.address.address2 and fn:length(location.address.address2) gt 0}">
          ${location.address.address2}<br/>
          </c:if>
          ${location.address.city}, ${location.address.provinceName} ${location.address.zipcode}<br/>
          ${location.address.countryObject.countryName}
    </div>
    <div class="grid-left padding-top20">
    	<h2 class="blue font14 non-bold padding-bottom10">Hardware to be shipped <button name="_eventId_back" class="button-gray" style="float:none;">Edit</button></h2>
    	<div id="review-container" style="width:300px">
          <table class="review" width="100%">
            <c:forEach items="${location.products}" var="product">                    
            <c:if test="${product.hardware and product.quantity gt 0}">
            <tr>
            	 <td>${product.name}</td>
            	 <td>${product.quantity}</td>
            </tr>
            </c:if>
            </c:forEach>
          </table>
      </div>
          <div class="clear padding-bottom40"></div>

          <h2 class="font14 bold black padding-bottom5">Shipping Method</h2>
          ${location.shippingMethod.name} - <fmt:formatNumber value="${location.shippingMethod.price}" type="currency" pattern="$0.00"/> (x ${location.hardwareUnits} units) = <fmt:formatNumber value="${location.shippingCost}" type="currency" pattern="$0.00"/>
          <div class="clear padding-bottom20"></div>
    </div>
    </c:if>
    </c:forEach>
    </c:if>

  <div class="clear padding-top10"></div>
   <div class="shopinformation-box" id="navButtons">
		<button type="submit" name="_eventId_back" value="Previous" class="button-green float-left">Previous</button>
		<button id="submitOrder" type="submit" name="_eventId_submitOrder" value="Place Your Order" class="button-green float-right">Place Your Order</button>
	</div>
    <div class="clear padding-top10"></div>
    <!-- Start note box -->
	<div class="simple-tips">
		<ul>
			<li><strong>${accountPayment.chargeMessage}</strong></li>
		</ul>
	</div>
	<!-- End note box -->
                     
  <!-- END CONTENT -->
  </div>
</form:form>