package com.mmall.service.impl;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mmall.common.Const;
import com.mmall.common.ResponseCode;
import com.mmall.common.ServerResponse;
import com.mmall.dao.CartMapper;
import com.mmall.dao.ProductMapper;
import com.mmall.pojo.Cart;
import com.mmall.pojo.Product;
import com.mmall.service.ICartService;
import com.mmall.util.BigDecimalUtil;
import com.mmall.util.Propertiesutil;
import com.mmall.vo.CartProductVo;
import com.mmall.vo.CartVo;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : Administrator
 * @create 2018-04-15 14:17
 */
@Service("iCartService")
public class CartServiceImpl implements ICartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;


    /**
     * 购物车的添加功能
     * @param userId
     * @param productId
     * @param count
     * @return
     */
    @Override
    public ServerResponse<CartVo> add(Integer userId, Integer productId, Integer count) {
        Cart cart = cartMapper.selectCartByUserIdProductId(userId, productId);
        if (cart == null) {
            // 这个产品不在购物车里面
            Cart cartItem = new Cart();
            cartItem.setQuantity(count);
            cartItem.setChecked(Const.Cart.CHECKED);
            cartItem.setProductId(productId);
            cartItem.setUserId(userId);
            cartMapper.insert(cartItem);
        } else {
            // 这个产品在购物车里面
            count = cart.getQuantity() + count;
            // 跟新数量
            cart.setQuantity(count);
            cartMapper.updateByPrimaryKeySelective(cart);
        }
        return this.getListProduct(userId);
    }


    /**
     * 购物车的添加功能--模块
     * @param userId
     * @param productId
     * @param count
     * @return
     */
    @Override
    public ServerResponse<CartVo> update(Integer userId, Integer productId, Integer count) {
        if (productId == null || count == null) {
            return ServerResponse.createByErrorCodeMassage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        Cart cart = cartMapper.selectCartByUserIdProductId(userId, productId);
        if (cart != null) {
            cart.setQuantity(count);
        }
        cartMapper.updateByPrimaryKey(cart);
        return this.getListProduct(userId);
    }


    /**
     * 删除商品
     * @param userId
     * @param productIds
     * @return
     */
    @Override
    public ServerResponse<CartVo> deleteProduct(Integer userId, String productIds) {
        List<String> productList = Splitter.on(",").splitToList(productIds);
        if (CollectionUtils.isEmpty(productList)) {
            return ServerResponse.createByErrorCodeMassage(ResponseCode.ILLEGAL_ARGUMENT.getCode(), ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        cartMapper.deleteByUserIdByUserIdProductIds(userId, productList);
        return this.getListProduct(userId);
    }

    /**
     * 查询购物车
     * @param userId
     * @return
     */
    @Override
    public ServerResponse<CartVo> getListProduct(Integer userId) {
        CartVo cartVo = this.getCartVoLimit(userId);
        return ServerResponse.createBySuccess(cartVo);
    }

    @Override
    public ServerResponse<CartVo> selectOrUnSelectAll(Integer userId, Integer prodcutId,Integer checked) {
        cartMapper.checkedOrUncheckedAllProdcut(userId,checked,prodcutId);
        return this.getListProduct(userId);
    }


    /**
     * 获得用户购物车里面的所有商品数量
     * @param userId
     * @return
     */
    @Override
    public ServerResponse<Integer> getcartProductCount(Integer userId) {
        if (userId == null) {
            return ServerResponse.createBySuccess(0);
        }
        return ServerResponse.createBySuccess(cartMapper.selectCartProductCount(userId));
    }


    /**
     *
     * @param userId
     * @return
     */
    private CartVo getCartVoLimit(Integer userId) {
        CartVo cartVo = new CartVo();
        List<Cart> cartList = cartMapper.selectCartByUserId(userId);
        List<CartProductVo> cartProductVoList = Lists.newArrayList();

        BigDecimal cartTotalPrice = new BigDecimal("0");
        if (CollectionUtils.isNotEmpty(cartList)) {

            for (Cart carItem : cartList) {
                CartProductVo cartProductVo = new CartProductVo();
                cartProductVo.setId(carItem.getId());
                cartProductVo.setUserId(userId);
                cartProductVo.setProductId(carItem.getProductId());
                Product product = productMapper.selectByPrimaryKey(carItem.getProductId());
                if (product != null) {
                    cartProductVo.setProdcutMainImage(product.getMainImage());
                    cartProductVo.setProductName(product.getName());
                    cartProductVo.setProductSubtitle(product.getSubtitle());
                    cartProductVo.setProductStatus(product.getStatus());
                    cartProductVo.setProductPrice(product.getPrice());
                    cartProductVo.setProductStock(product.getStock());
                    // 判断库存
                    int buyLimitCount = 0;
                    if (product.getStock() >= carItem.getQuantity()) {
                        // 库存充足的时候
                        buyLimitCount = carItem.getQuantity();
                        cartProductVo.setLimitQuantity(Const.Cart.LIMIT_MUM_SUCCESS);
                    } else {
                        buyLimitCount = product.getStock();
                        cartProductVo.setLimitQuantity(Const.Cart.LIMIT_MUM_FAIL);
                        // 购物车中更新有效库存
                        Cart cartForQuantity = new Cart();
                        cartForQuantity.setId(carItem.getId());
                        cartForQuantity.setQuantity(buyLimitCount);
                        cartMapper.updateByPrimaryKeySelective(cartForQuantity);
                    }
                    cartProductVo.setQuantity(buyLimitCount);
                    // 计算总价
                    cartProductVo.setProductTotalPrice(BigDecimalUtil.mul(product.getPrice().doubleValue(), cartProductVo.getQuantity()));
                    cartProductVo.setProductChecked(carItem.getChecked());
                }

                if (carItem.getChecked() == Const.Cart.CHECKED) {
                     // 如果已经勾选，增加到总价之中
                    cartTotalPrice = BigDecimalUtil.add(cartTotalPrice.doubleValue(), cartProductVo.getProductTotalPrice().doubleValue());
                }
                cartProductVoList.add(cartProductVo);
            }
        }
        cartVo.setCartTotalPrice(cartTotalPrice);
        cartVo.setCartProductVoList(cartProductVoList);
        cartVo.setAllChecked(this.getAllChedSatus(userId));
        cartVo.setImageHost(Propertiesutil.getProperty("ftp.server.http.prefix"));
        return cartVo;
    }

    /**
     *
     * @param userId
     * @return
     */
    private boolean getAllChedSatus(Integer userId) {
        return userId != null && cartMapper.selesctCartProductCheckedStatusByUserId(userId) == 0;
    }
}
