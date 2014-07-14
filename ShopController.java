package com.vocalocity.hdap.web.controller;

import com.vocalocity.hdap.billing.domain.PaymentsMethodWrapper;
import com.vocalocity.hdap.domain.account.AccountPayment;
import com.vocalocity.hdap.domain.account.Country;
import com.vocalocity.hdap.domain.shop.catalog.ProductCatalog;
import com.vocalocity.hdap.domain.shop.catalog.RatePlan;
import com.vocalocity.hdap.domain.shop.order.Address;
import com.vocalocity.hdap.domain.shop.order.CreateOrderRequest;
import com.vocalocity.hdap.domain.shop.order.Order;
import com.vocalocity.hdap.remote.RestException;
import com.vocalocity.hdap.remote.RestInvoker;
import com.vocalocity.hdap.security.utils.HdapSecurityUtility;
import com.vocalocity.hdap.service.AccountService;
import com.vocalocity.hdap.service.ShopService;
import com.vocalocity.hdap.util.StringUtils;
import com.vocalocity.hdap.web.form.shop.OrderForm;
import com.vocalocity.hdap.web.form.shop.ShippingForm;
import com.vocalocity.hdap.web.form.shop.ShippingMethod;
import com.vocalocity.hdap.web.webflow.ShopFlowUrlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.webflow.core.collection.ParameterMap;
import org.springframework.webflow.execution.RequestContext;
import org.springframework.webflow.execution.RequestContextHolder;
import org.springframework.webflow.mvc.servlet.MvcExternalContext;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@Controller("shopController")
@RequestMapping("/account/{accountId}")
public class ShopController {
	private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    RestInvoker restInvoker;

	@Autowired
	private ShopService shopService;
	
	@Autowired
	private AccountService accountService;
	
	private ShopFlowUrlHelper urlHelper = new ShopFlowUrlHelper();

	@RequestMapping("/shop/productCatalog")
	public @ResponseBody ProductCatalog getProductCatalog(@PathVariable Integer accountId) {
		return shopService.getProductCatalog(accountId);
	}
	
	@RequestMapping(value="/shop/shippingOptions")
	public @ResponseBody List<RatePlan> getShippingOptions(@PathVariable Integer accountId, @RequestParam("countryId")String countryId, @RequestParam("provinceId")int provinceId, @RequestParam("hardwareModels")String hardwareModels) {
		List<String> models = StringUtils.parseStringList(hardwareModels);
		return models.size() > 0 ? shopService.getShippingProducts(accountId, models, countryId, provinceId) : new ArrayList<RatePlan>();
	}

    @RequestMapping("/purchaseProduct")
    public @ResponseBody Order purchaseProduct(@PathVariable Integer accountId, @RequestParam("ratePlanId")String ratePlanId) {
        log.info("Purchase product of {} for account {}", ratePlanId, accountId);
        CreateOrderRequest createOrderRequest = new CreateOrderRequest();
        createOrderRequest.addOrderItem(ratePlanId);
        Order order = shopService.createDefaultOrder(accountId, createOrderRequest);
        Order newOrder =  shopService.submitOrder(order);
        log.info("Successfully placed an order, order ID: {}", newOrder.getOrderId());
        return newOrder;
    }
		
	public OrderForm initializeOrder(MvcExternalContext externalContext) {
		OrderForm orderForm = null;
		CreateOrderRequest createOrderRequest = getCreateOrderRequest(externalContext);
        int accountId = getAccountId(externalContext);
        ProductCatalog catalog = getProductCatalog(accountId);
		if (createOrderRequest != null && createOrderRequest.hasOrderItems()) {
			Order order = shopService.createDefaultOrder(accountId, createOrderRequest, catalog);
			orderForm = new OrderForm(order, catalog);
		}
		else {
			orderForm = new OrderForm();
            orderForm.setCatalogId(catalog.getCatalogId());
		}
		initializeOrder(orderForm, externalContext);
				
		return orderForm;
	}
	
	private CreateOrderRequest getCreateOrderRequest(MvcExternalContext externalContext) {
		ParameterMap map = externalContext.getRequestParameterMap();
		CreateOrderRequest createOrderRequest = null;
		if (!map.isEmpty()) {
			createOrderRequest = new CreateOrderRequest();
			String ratePlanId = map.get("ratePlanId");
			if (ratePlanId != null) {
				createOrderRequest.addOrderItem(ratePlanId);
			}
            else if (map.get("productName") != null) {
                String productName = map.get("productName");
                createOrderRequest.addOrderItemByProductName(productName);
            }
            else if (map.get("ratePlanName") != null) {
                String ratePlanName = map.get("ratePlanName");
                createOrderRequest.addOrderItemByRatePlanName(ratePlanName);
            }
		}
		return createOrderRequest;
	}
	
	public OrderForm loadOrder(MvcExternalContext externalContext) {
		OrderForm orderForm = null;
		log.info("Load order...");
		RequestContext requestContext = RequestContextHolder.getRequestContext();		
		Object obj =  requestContext.getConversationScope().get("order");
		if (obj instanceof OrderForm) {
			orderForm = (OrderForm)obj;
		}
		else {
			log.info("Create default order in loadOrder()...");
			orderForm = initializeOrder(externalContext);
		}
		
		// b/c of a WebFlow bug, the properties of objects stored in conversation scope are not available after deserialization
		// Look at LocalAttributeMap, the "attributeAccessor" is transient, and this field is needed for evaluating expressions
		// so just programmatically set the object
		requestContext.getConversationScope().put("accountId", orderForm.getAccountId());	
		
		return orderForm;
	}
	
	private void initializeOrder(OrderForm order, MvcExternalContext externalContext) {
		// set account ID
		int accountId = getAccountId(externalContext);
		order.setAccountId(accountId);

		// initialize payment information
		AccountPayment account = accountService.getAccountPayment(order.getAccountId());
		if (account != null) {
			if(account.getExternalBillingDefaultPaymentMethod() != null) {
				order.setPaymentMethodType(account.getExternalBillingDefaultPaymentMethod().getType());
			}			
		}
		
		// initialize company address
		order.setCompanyAddress(new Address(account));
		
		// get the referrer
		HttpServletRequest request = ((HttpServletRequest)externalContext.getNativeRequest());
        String returnUrl = request.getContextPath() + "/account/" + order.getAccountId() + "/manage-service";

        order.setReturnUrl(returnUrl);
	}
	
	public void addProduct(OrderForm orderForm) {
		orderForm.addProduct(orderForm.getProduct(), getProductCatalog(orderForm.getAccountId()));
		
		// clean up the temporary product list
		orderForm.reset();
	}
	
	public void updateShopCart(OrderForm order) {
		order.updateItemQuantities();
	}

    public boolean isEligibleToShop(OrderForm order){
        return isNotBilled() || isAccountBillingReady(order);
    }

    private boolean isNotBilled(){
        return !HdapSecurityUtility.getCurrentAccount().isBilledCustomer();
    }
	
	public boolean isAccountBillingReady(OrderForm order) {
		return order.getPaymentMethodType() != null;
	}
	
	public boolean isShippingNeeded(OrderForm order) {
		return order.isShippingNeeded();
	}
	
	public List<Country> getCountries() {
		return shopService.getCountries();
	}
	
	public OrderForm initShippingLocations(OrderForm order, ProductCatalog catalog) {
		if (order.getShipping() == null) {
			order.setShipping(new ShippingForm(order.getItems(), order.getCompanyAddress()));
		}
		else {
			// the shop cart items might not match with locations in ShippingForm,
			// this happens when user nav back to catalog from shipping page and change the shop cart
			order.getShipping().updateAllProducts(order.getItems());
		}

		return updateShipping(catalog, order);
	}

	public OrderForm addLocation(OrderForm order) {
		order.getShipping().addLocation(order.getItems(), order.getCompanyAddress());
		return updateShipping(getProductCatalog(order.getAccountId()), order);
	}

	public OrderForm removeLocation(OrderForm order) {
		if (order.getShipping().canRemoveLocation()) {
			order.getShipping().removeLocation(order.getItems());
		}
		
		return updateShipping(getProductCatalog(order.getAccountId()), order);
	}

	private OrderForm updateShipping(ProductCatalog catalog, OrderForm order) {
		// fill with company address if needed
//		order.getShipping().updateDefaultAddress(order.getCompanyAddress());
		
		for (com.vocalocity.hdap.web.form.shop.Location location : order.getShipping().getLocations()) {			
			updateShippingMethods(order.getAccountId(), catalog, location);
		}
		return order;
	}
	
	private void updateShippingMethods(int accountId, ProductCatalog catalog, com.vocalocity.hdap.web.form.shop.Location location) {

        if(location == null || location.getAddress() == null || location.getAddress().getCountryId() == null
                || location.getAddress().getProvinceId() == null){
            throw new IllegalArgumentException("To update shipping methods, both the countryId and provinceId must be provided." +
                    "  Please update your account information.");
        }

        List<String> hardwareModels = location.getHardwareModels();
		
		ArrayList<ShippingMethod> shippingMethods = new ArrayList<ShippingMethod>(); 
		if (hardwareModels.size() > 0) {
			List<RatePlan> shippingRatePlans = shopService.getShippingProducts(accountId, catalog, hardwareModels, location.getAddress().getCountryId(), location.getAddress().getProvinceId());
			for (RatePlan ratePlan : shippingRatePlans) {
				shippingMethods.add(new ShippingMethod(ratePlan));
			}			
		}
		location.setShippingMethods(shippingMethods);
	}

	@ModelAttribute("paymentMethodList")
    static PaymentsMethodWrapper getPaymentMethods(String accountId, RestInvoker restInvoker) {
        return restInvoker.getObject("paymentMethod/retrieve?accountId={accountId}", PaymentsMethodWrapper.class, accountId).build();
    }
	
	public OrderForm updateReviewInfo(OrderForm order) {
		// if there are no hardware in the cart, then the locations needs to be populated
		ShippingForm shipping = order.getShipping();
		if (order.getShipping() == null) {
			order.setShipping(new ShippingForm(order.getItems(), order.getCompanyAddress()));
			shipping = order.getShipping();
		}

		// update countries for the country name display
		shipping.updateAllProducts(order.getItems());
		shipping.updateAddressCountryNames(getCountries());			
		
		// update account information, billing, payment methods and put in request scope
		AccountPayment account = accountService.getAccountPayment(order.getAccountId());
		RequestContextHolder.getRequestContext().getRequestScope().put("accountPayment", account);

        try {
            if(!order.isSubmitResult())	order.setSubmitOrderError("");
            Order previewOrder = shopService.previewOrder(order.createOrder());
            order.setPreviewOrder(previewOrder);
        } catch (Exception e) {
            log.error("Exception thrown while PREVIEWING order: " + order, e);
            order.setSubmitOrderError(e.getMessage());
        }
        order.setIsSubmitResult(false);
        // order.setPaymentMethodList(ShopController.getPaymentMethods(order.getAccountId().toString(),restInvoker).getPaymentMethods());
		return order;
	}
	
	public boolean submitOrder(OrderForm orderForm) {
		Order order = null;
		Order newOrder = null;
        orderForm.setIsSubmitResult(true);
		try {
			// create Order from OrderForm
			order = orderForm.createOrder();
			orderForm.setSubmitOrderError(null);
			newOrder = shopService.submitOrder(order);
			log.debug("Order created: {}", newOrder);
		}
		catch(ParseException pe) {
			log.error("createOrder()", pe);
		}
        catch(RestException re) {
            log.error("Exception thrown for order", re);
            log.error("Exception thrown while submitting order {}", newOrder);
            orderForm.setSubmitOrderError(re.getMessage());
            if (re.getEnglishErrorMessageFound()) {
              return false;
            }
        }

		if ((newOrder == null || newOrder.getOrderId() <= 0) && orderForm.getSubmitOrderError() == null) {
			// failed placed order
			orderForm.setSubmitOrderError("Failed to submit order");
		}
        return (null == orderForm.getSubmitOrderError());
	}
	
	private int getAccountId(MvcExternalContext externalContext) {
		try {
			int accountId = Integer.parseInt(urlHelper.getAccountId(getRequest(externalContext)));
			return accountId;
		} catch(NumberFormatException e) {
			log.error("getAccountId()", e);
			return 1;
		}
	}
	
	private HttpServletRequest getRequest(MvcExternalContext externalContext) {
		return (HttpServletRequest)externalContext.getNativeRequest();
	}
}
