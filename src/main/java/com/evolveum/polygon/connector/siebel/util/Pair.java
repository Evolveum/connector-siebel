package com.evolveum.polygon.connector.siebel.util;

/**
 * Pair of values.
 * 
 * @param  <A>  type of the first value
 * @param  <B>  type of the second value
 *
 * @author  Marián Petráš
 */
public class Pair<A,B> {

	public final A a;

	public final B b;

	public Pair(final A a,
	            final B b) {
		this.a = a;
		this.b = b;
	}

}
