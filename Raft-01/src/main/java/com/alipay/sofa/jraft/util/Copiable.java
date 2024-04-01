package com.alipay.sofa.jraft.util;

/**
 * 提供了深拷贝的接口，实现该接口的方法都要进行深拷贝
 * @param <T>
 */
public interface Copiable<T> {


    T copy();
}