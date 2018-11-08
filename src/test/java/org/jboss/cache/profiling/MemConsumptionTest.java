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
package org.jboss.cache.profiling;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Random;

/**
 * Importnat - make sure you inly enable these tests locally!
 */
@Test(groups = "profiling", testName = "profiling.MemConsumptionTest", enabled = false)
public class MemConsumptionTest
{
   // adjust the next 4 values
   int numNodes = 1000000;
   int payloadSize = 20; // characters
   int keySize = 10; // characters
   PayloadType payloadType = PayloadType.STRINGS;

   enum PayloadType {STRINGS, BYTE_ARRAYS}

   int bytesPerCharacter = 2;

   Random r = new Random();

   public void testMemConsumption() throws IOException
   {      
      int kBytesCached = (bytesPerCharacter * numNodes * (payloadSize + keySize)) / 1024;
      System.out.println("Bytes to be cached: " + NumberFormat.getIntegerInstance().format(kBytesCached) + " kb");

      Cache c = new DefaultCacheFactory().createCache(false); // default LOCAL cache
      c.start();
      for (int i = 0; i < numNodes; i++)
      {
         switch (payloadType)
         {
            case STRINGS:
               c.put(Fqn.fromString("/node" + i), generateRandomString(keySize), generateRandomString(payloadSize));
               break;
            case BYTE_ARRAYS:
               c.put(Fqn.fromString("/node" + i), generateUniqueKey(i, keySize), generateBytePayload(payloadSize));
               break;
            default:
               throw new CacheException("Unknown payload type");
         }

         if (i % 1000 == 0) System.out.println("Added " + i + " entries");
      }

      System.out.println("Calling System.gc()");
      System.gc();  // clear any unnecessary objects

      TestingUtil.sleepThread(1000); // wait for gc

      // wait for manual test exit
      System.out.println("Cache populated; check mem usage using jconsole, etc.!");
      System.in.read();
   }

   private String generateRandomString(int stringSize)
   {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < stringSize; i++)
      {
         sb.append(r.nextInt(9)); // single digit
      }
      assert sb.length() == stringSize;
      return sb.toString();
   }

   private byte[] generateUniqueKey(int runNumber, int keySize)
   {
      byte[] b = new byte[keySize];
      b[0] = (byte) (runNumber >>> 0);
      b[1] = (byte) (runNumber >>> 8);
      b[2] = (byte) (runNumber >>> 16);
      b[3] = (byte) (runNumber >>> 24);

      for (int i = 4; i < keySize; i++) b[i] = 0;
      return b;
   }

   private byte[] generateBytePayload(int payloadSize)
   {
      byte[] b = new byte[payloadSize];
      Arrays.fill(b, (byte) 0);
      return b;
   }

}
