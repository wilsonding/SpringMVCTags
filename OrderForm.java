package com.vocalocity.hdap.web.form.shop;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vocalocity.hdap.billing.domain.BaseInvoice;
import com.vocalocity.hdap.billing.domain.PaymentMethod;
import com.vocalocity.hdap.domain.shop.catalog.BaseProduct;
import org.springframework.binding.message.MessageContext;
import org.springframework.binding.validation.ValidationContext;

import com.vocalocity.hdap.domain.shop.catalog.ProductCatalog;
import com.vocalocity.hdap.domain.shop.order.Address;
import com.vocalocity.hdap.domain.shop.order.Location;
import com.vocalocity.hdap.domain.shop.order.Order;
import com.vocalocity.hdap.web.spring.MessageUtils;

public class OrderForm implements Serializable{
	private static final long serialVersionUID = -2465864877856484908L;

    private Order previewOrder;
	private String returnUrl;
	private String catalogId;
	private Integer accountId;
	private String paymentMethodType;
	private Address companyAddress;
	private String dateBooked;
    private boolean isSubmitResult;
    //private List<PaymentMethod> paymentMethodList;
    //private Integer paymentMethodId;
    private String paymentMethodId;

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    /*public List<PaymentMethod> getPaymentMethodList() {
        return paymentMethodList;
    }

    public void setPaymentMethodList(List<PaymentMethod> paymentMethodList) {
        this.paymentMethodList = paymentMethodList;
    }*/

    public boolean isSubmitResult(){
        return isSubmitResult;
    }

    public void setIsSubmitResult(boolean isSubmitResult){
        this.isSubmitResult = isSubmitResult;
    }

	private LineItem product;
	
	// for Review Cart page
	private ShopCart shopCart;
	
	// for shipping page
	private ShippingForm shipping;
	
	// error message 
	private String submitOrderError;

	public OrderForm() {
		shopCart = new ShopCart();
	}

    public void setPreviewOrder(Order order){
        this.previewOrder = order;
    }

    public Order getPreviewOrder(){
        return this.previewOrder;
    }
    
    public BaseInvoice getPreviewInvoice(){
		return this.previewOrder != null ? this.previewOrder.getInvoice() : null;
	}

	public OrderForm(Order order, ProductCatalog catalog) {
		this();
		this.catalogId = order.getCatalogId();
		
		for (Location location : order.getLocations()) {
			shopCart.addFromProducts(location.getProducts(), catalog);
		}
	}
	
	public void addProduct(LineItem product, ProductCatalog catalog) {
		addProducts(Collections.singletonList(product), catalog);
	}
	
	public void addProducts(List<LineItem> products, ProductCatalog catalog) {
		shopCart.addProducts(products, catalog);		
	}
	
	public void updateItemQuantities() {
		shopCart.updateItemQuantities();
	}
	
	public void reset() {
		if (product != null) {
			product.reset();
		}
	}

    public void resetPreviewOrder() {
        this.previewOrder = null;
    }
	
	public Order createOrder() throws ParseException {
		Order order = new Order();
		
		order.setAccountId(accountId);
		order.setCatalogId(catalogId);

		order.setDateBookedString(dateBooked);

		ArrayList<Location> locations = new ArrayList<Location>();
		if (shipping != null) {
			shipping.removeEmptyLocations();
			for (com.vocalocity.hdap.web.form.shop.Location loc : shipping.getLocations()) {
				// fill with company address if needed
				if (loc.getSameAsCompanyAddress() == null || loc.getSameAsCompanyAddress()) {
					loc.setAddress(companyAddress);
				}

				Location location = loc.createLocation();
				locations.add(location);
			}			
		}

		order.setLocations(locations);
		
		return order;
	}
	
	public void validateReview(ValidationContext context) {
		if (context.getUserEvent().equals("next")) {
			if (shopCart.isEmpty()) {
				MessageUtils.buildErrorMessage(context.getMessageContext(), "", "shop.cart.empty");
			}			
		}
	}

	public void validateCatalog(ValidationContext context) {
		String event = context.getUserEvent();
		MessageContext messageContext = context.getMessageContext();
		if (event.equals("addProduct")) {
			if (product.getQuantity() == 0) {
				MessageUtils.buildErrorMessage(messageContext, "product.quantity", "shop.product.quantity.empty");
			}
		}
		else if (event.equals("next")) {
			if (shopCart.isEmpty()) {
				MessageUtils.buildErrorMessage(messageContext, "cart", "shop.cart.empty");
			}
		}
    }

    public boolean validateAddProduct(MessageContext messageContext, ProductCatalog catalog) {

        Integer addedProductQuantity = product.getQuantity();
        boolean isBeyondMax = isAddedProductOverLimit(addedProductQuantity, catalog);
        if (isBeyondMax) {
//            log.debug("wbh: showing error for: " + product.toString());
            MessageUtils.buildErrorMessage(messageContext, "product.quantity", "shop.product.quantity.maxExceeded"
                    , new Object[]{catalog.getProduct(product.getProductId()).getName(),catalog.getProduct(product.getProductId()).getPurchaseQuantityLimit()});
        }
        //spring needs this result
        return !isBeyondMax;
    }

    public boolean isAddedProductOverLimit(Integer addedProductQuantity, ProductCatalog catalog) {
        boolean overLimit = false;
        Integer cartQuantity;
        BaseProduct selectedProduct;
        Integer limit;
        String productId = product.getProductId();
        cartQuantity = shopCart.getQuantityForProductId(productId);

        selectedProduct = catalog.getProduct(productId);
        limit = selectedProduct.getPurchaseQuantityLimit();

        if (selectedProduct.isPurchaseLimited() && ((addedProductQuantity + cartQuantity) > limit)) {
            overLimit = true;
        }
        return overLimit;
    }

    public boolean validateCart(MessageContext messageContext, ProductCatalog catalog){

        List<String> overLimitProductsFromCart = getOverLimitProductsFromCart(catalog);
//        log.debug("wbh: productList: " + overLimitProductsFromCart);
        boolean hasOverLimitProducts = overLimitProductsFromCart.size() > 0;
        if (hasOverLimitProducts) {
            for(String name : overLimitProductsFromCart){
//            log.debug("wbh: showing error for: " + name);
            MessageUtils.buildErrorMessage(messageContext, "product.quantity", "shop.product.quantity.maxExceeded"
                    , new Object[]{name,catalog.getProductByName(name).getPurchaseQuantityLimit()});
            }
        }
        return !hasOverLimitProducts;
    }

    public List<String> getOverLimitProductsFromCart(ProductCatalog catalog) {

        List<String> productList = new ArrayList<String>();

        Integer cartQuantity;
        BaseProduct selectedProduct;
        Integer limit;
        for (LineItem item : shopCart.getItems()) {

            cartQuantity = item.getQuantity();
            selectedProduct = catalog.getProduct(item.getProductId());
            limit = selectedProduct.getPurchaseQuantityLimit();

            if (selectedProduct.isPurchaseLimited() && (cartQuantity > limit)) {
                productList.add(selectedProduct.getName());
            }
        }
        return productList;
    }

    public void validateShipping(ValidationContext context) {
		if (context.getUserEvent().equals("next")) {
			validateShipping(shopCart, context.getMessageContext());
		}
	}
	
	public void validateShipping(ShopCart shopCart, MessageContext messageContext) {
		if (shipping != null) {
			shipping.validate(shopCart.getItems(), messageContext);
		}
	}

    public boolean validateAddresses(MessageContext context){
        boolean isValid =  companyAddress.isValid();
        if(!isValid){
            MessageUtils.buildErrorMessage(context, "invalidAddress", "shop.shipping.location.invalidAddress");
        }
        return isValid;
    }

	
	public boolean isShippingNeeded() {
		return shopCart.isShippingNeeded();
	}
	
	public List<LineItem> getItems() {
		return shopCart.getItems();
	}
	
	public String getCatalogId() {
		return catalogId;
	}

	public void setCatalogId(String catalogId) {
		this.catalogId = catalogId;
	}

	public Integer getAccountId() {
		return accountId;
	}

	public void setAccountId(Integer accountId) {
		this.accountId = accountId;
	}

	public BigDecimal getTotalMonthlyFee() {
		return shopCart.getTotalMonthlyFee();
	}

	public BigDecimal getTotalOneTimeFee() {
        if (previewOrder != null && previewOrder.getInvoice() != null) {
            return previewOrder.getInvoice().getAmount();
        }
        return shopCart.getTotalOneTimeFee();
	}

	public ShippingForm getShipping() {
		return shipping;
	}

	public void setShipping(ShippingForm shipping) {
		this.shipping = shipping;
	}

	public String getPaymentMethodType() {
		return paymentMethodType;
	}

	public void setPaymentMethodType(String paymentMethodType) {
		this.paymentMethodType = paymentMethodType;
	}
	
	public LineItem getProduct() {
		return product;
	}

	public void setProduct(LineItem product) {
		this.product = product;
	}

	public Address getCompanyAddress() {
		return companyAddress;
	}

	public void setCompanyAddress(Address companyAddress) {
		this.companyAddress = companyAddress;
	}

	public String getSubmitOrderError() {
		return submitOrderError;
	}

	public void setSubmitOrderError(String submitOrderError) {
		this.submitOrderError = submitOrderError;
	}
	
	public ShopCart getShopCart() {
		return shopCart;
	}

	public void setShopCart(ShopCart shopCart) {
		this.shopCart = shopCart;
	}

	public String getReturnUrl() {
		return returnUrl;
	}

	public void setReturnUrl(String returnUrl) {
		this.returnUrl = returnUrl;
	}

	public String getDateBooked() {
		return dateBooked;
	}

	public void setDateBooked(String dateBooked) {
		this.dateBooked = dateBooked;
	}
}
