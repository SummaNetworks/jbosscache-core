/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.cache.config.parsing;

import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.CustomInterceptorConfig;
import org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor;
import org.jboss.cache.config.parsing.custominterceptors.BbbCustomInterceptor;
import org.jboss.cache.config.parsing.element.CustomInterceptorsElementParser;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Tester class for {@link CustomInterceptorsElementParser}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test (groups = "unit", testName = "config.parsing.CustomInterceptorsElementParserTest")
public class CustomInterceptorsElementParserTest
{
   CustomInterceptorsElementParser parser = new CustomInterceptorsElementParser();

   /**
    * Tests a correct configuration having all possible elements/attributes.
    * @throws Exception
    */
   public void testFullConfiguration() throws Exception
   {
      String xml =
               "   <customInterceptors>\n" +
               "      <interceptor position=\"first\" class=\"org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor\">\n" +
               "         <property name=\"attrOne\" value=\"value1\"></property>\n" +
               "         <property name=\"attrTwo\" value=\"value2\"></property>\n" +
               "         <property name=\"attrThree\" value=\"value3\"></property>\n" +
               "      </interceptor>\n" +
               "      <interceptor position=\"last\" class=\"org.jboss.cache.config.parsing.custominterceptors.BbbCustomInterceptor\"/>\n" +
               "      <interceptor index=\"3\" class=\"org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor\"/>\n" +
               "      <interceptor before=\"org.jboss.cache.interceptors.CallInterceptor\"\n" +
               "                   class=\"org.jboss.cache.config.parsing.custominterceptors.BbbCustomInterceptor\"/>\n" +
               "      <interceptor after=\"org.jboss.cache.interceptors.CallInterceptor\"\n" +
               "                   class=\"org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor\"/>\n" +
               "   </customInterceptors>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      List<CustomInterceptorConfig> configs = parser.parseCustomInterceptors(element);
      assert configs.size() == 5;
      CustomInterceptorConfig one = configs.get(0);
      assert one.isFirst();
      assert one.getInterceptor() instanceof AaaCustomInterceptor;
      assert ((AaaCustomInterceptor)one.getInterceptor()).getAttrOne().equals("value1");
      assert ((AaaCustomInterceptor)one.getInterceptor()).getAttrTwo().equals("value2");
      assert ((AaaCustomInterceptor)one.getInterceptor()).getAttrThree().equals("value3");

      CustomInterceptorConfig two = configs.get(1);
      assert !two.isFirst();
      assert two.isLast();
      assert two.getInterceptor() instanceof BbbCustomInterceptor;

      CustomInterceptorConfig three = configs.get(2);
      assert !three.isFirst();
      assert !three.isLast();
      assert three.getIndex() == 3;

      CustomInterceptorConfig four = configs.get(3);
      assert !four.isFirst();
      assert !four.isLast();
      assert four.getBeforeClass().equals("org.jboss.cache.interceptors.CallInterceptor");

      CustomInterceptorConfig five = configs.get(4);
      assert !five.isFirst();
      assert !five.isLast();
      assert five.getAfterClass().equals("org.jboss.cache.interceptors.CallInterceptor");
   }

   /**
    * trying to specify an attribute that does not exist on the interceptor class.
    */
   public void testWrongAttribute() throws Exception
   {
      String xml =
            "   <customInterceptors>\n" +
            "      <interceptor position=\"first\" class=\"org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor\">\n" +
            "         <property name=\"nonexistenAttribute\" value=\"value3\"></property>\n" +
            "      </interceptor>\n" +
            "   </customInterceptors>";
      Element el = XmlConfigHelper.stringToElementInCoreNS(xml);
      try
      {
         parser.parseCustomInterceptors(el);
         assert false: "exception expected";  
      } catch (ConfigurationException e)
      {
         //expected
      }
   }

   /**
    * If the interceptor class is incorrect (e.g. does not extend the CommandInterceptor base class) then parser should fail.
    */
   public void testBadInterceptorClass() throws Exception
   {
      String xml =
                  "   <customInterceptors>\n" +
                  "      <interceptor position=\"first\" class=\"org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor\">\n" +
                  "         <property name=\"notExists\" value=\"value1\"></property>\n" +
                  "      </interceptor>\n" +
                  "   </customInterceptors>";
      Element el = XmlConfigHelper.stringToElementInCoreNS(xml);
      try
      {
         parser.parseCustomInterceptors(el);
         assert false: "exception expected";
      } catch (ConfigurationException e)
      {
         //expected
      }
   }
}
