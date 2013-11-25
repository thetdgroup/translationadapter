package com.thetdgroup;

/**
 * Property JackRabbitPropertyOperator
 * 
 * Define the Operators supported by JackRabbit when searching on Properties
 * 
 * 
 */
public final class JackRabbitPropertyOperator
{
	/**
	 * Property Operator
	 * 
	 * AND
	 * <p>
	 * And's the next statement
	 * <p>
	 * OR
	 * <p>
	 * Or's the next statement
	 * <p>
	 * 
	 * @since 1.0 
	 */	
		public static String PROPERTY_OPERATOR = "property_operator";
		public enum operator {AND, OR};
}
