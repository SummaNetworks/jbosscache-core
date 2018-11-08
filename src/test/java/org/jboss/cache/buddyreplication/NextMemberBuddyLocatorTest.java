/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jgroups.Address;
import org.jgroups.stack.IpAddress;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Tests the NextMemberBuddyLocator
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", sequential = true, testName = "buddyreplication.NextMemberBuddyLocatorTest")
public class NextMemberBuddyLocatorTest
{
   private IpAddress dataOwner;
   private List<Address> buddies_localhost, buddies_same_host_different_nic, buddies_different_hosts;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      buddies_localhost = new LinkedList<Address>();
      buddies_same_host_different_nic = new LinkedList<Address>();
      buddies_different_hosts = new LinkedList<Address>();

      try
      {
         dataOwner = new IpAddress(InetAddress.getByName("localhost"), 1000);
         buddies_localhost.add(new IpAddress(InetAddress.getByName("localhost"), 2000));
         buddies_localhost.add(new IpAddress(InetAddress.getByName("localhost"), 3000));
         buddies_localhost.add(new IpAddress(InetAddress.getByName("localhost"), 4000));
         buddies_localhost.add(new IpAddress(InetAddress.getByName("localhost"), 5000));

         // lets get a few more interfaces from the current host
         Enumeration en = NetworkInterface.getNetworkInterfaces();
         while (en.hasMoreElements())
         {
            NetworkInterface i = (NetworkInterface) en.nextElement();
            for (Enumeration en2 = i.getInetAddresses(); en2.hasMoreElements();)
            {
               InetAddress addr = (InetAddress) en2.nextElement();
               if (addr.isLoopbackAddress() || addr instanceof Inet6Address) continue;
               buddies_same_host_different_nic.add(new IpAddress(addr, 1000));
            }
         }

         // now lets get some which are definitely on different hosts
         // don't bother with DNS lookups - just use dummy IP addresses.
         buddies_different_hosts.add(new IpAddress(InetAddress.getByName("61.62.63.64"), 1000));
         buddies_different_hosts.add(new IpAddress(InetAddress.getByName("81.82.83.84"), 1000));
         buddies_different_hosts.add(new IpAddress(InetAddress.getByName("101.102.103.104"), 1000));
         buddies_different_hosts.add(new IpAddress(InetAddress.getByName("121.122.123.124"), 1000));
      }
      catch (Exception e)
      {
         throw e;
      }
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      buddies_localhost = null;
      buddies_same_host_different_nic = null;
      buddies_different_hosts = null;
      dataOwner = null;
   }


   private List getBuddies(int numBuddies, boolean ignoreColoc, List<Address> candidates)
   {
      return getBuddies(numBuddies, ignoreColoc, candidates, null);
   }

   private List getBuddies(int numBuddies, boolean ignoreColoc, List<Address> candidates, Map<Address, String> buddyPool)
   {
      NextMemberBuddyLocatorConfig cfg = new NextMemberBuddyLocatorConfig();
      cfg.setIgnoreColocatedBuddies(ignoreColoc);
      cfg.setNumBuddies(numBuddies);
      NextMemberBuddyLocator nmbl = new NextMemberBuddyLocator();
      nmbl.init(cfg);
      return nmbl.locateBuddies(buddyPool, candidates, dataOwner);
   }

   // without colocation

   public void testSingleBuddyNoColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      List results = getBuddies(1, false, list);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
   }

   public void testThreeBuddiesNoColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_localhost.get(3));

      List results = getBuddies(3, false, list);

      assertEquals(3, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
      assertEquals(buddies_localhost.get(2), results.get(2));
   }

   public void testMoreBuddiesThanAvblNoColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));

      List results = getBuddies(3, false, list);

      assertEquals(2, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
   }

   // with colocation, but all candidates are on the same host
   public void testSingleBuddyWithColocAllCandidatesColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      List results = getBuddies(1, true, list);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
   }

   public void testThreeBuddiesWithColocAllCandidatesColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_localhost.get(3));

      List results = getBuddies(3, true, list);

      assertEquals(3, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
      assertEquals(buddies_localhost.get(2), results.get(2));
   }

   public void testMoreBuddiesThanAvblWithColocAllCandidatesColoc()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));

      List results = getBuddies(3, true, list);

      assertEquals(2, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
   }

   // with colocation, all candidates are on the same host but with different NICs
   public void testSingleBuddyWithColocAllCandidatesColocDiffNics()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.addAll(buddies_same_host_different_nic);
      List results = getBuddies(1, true, list);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
   }

   public void testThreeBuddiesWithColocAllCandidatesColocDiffNics()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_localhost.get(3));
      list.addAll(buddies_same_host_different_nic);

      List results = getBuddies(3, true, list);

      assertEquals(3, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
      assertEquals(buddies_localhost.get(2), results.get(2));
   }

   public void testMoreBuddiesThanAvblWithColocAllCandidatesColocDiffNics()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.addAll(buddies_same_host_different_nic);

      List results = getBuddies(3, true, list);

      assertEquals(buddies_same_host_different_nic.isEmpty() ? 2 : 3, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
      assertEquals(buddies_localhost.get(1), results.get(1));
      if (!buddies_same_host_different_nic.isEmpty())
         assertEquals(buddies_same_host_different_nic.get(0), results.get(2));
   }

   // now for some non-colocated buddies to pick from
   public void testSingleBuddyWithColocDiffHosts()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.addAll(buddies_same_host_different_nic);
      list.addAll(buddies_different_hosts);
      List results = getBuddies(1, true, list);

      assertEquals(1, results.size());
      assertEquals(buddies_different_hosts.get(0), results.get(0));
   }

   public void testThreeBuddiesWithColocDiffHosts()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_localhost.get(3));
      list.addAll(buddies_same_host_different_nic);
      list.addAll(buddies_different_hosts);

      List results = getBuddies(3, true, list);

      assertEquals(3, results.size());
      assertEquals(buddies_different_hosts.get(0), results.get(0));
      assertEquals(buddies_different_hosts.get(1), results.get(1));
      assertEquals(buddies_different_hosts.get(2), results.get(2));
   }

   public void testMoreBuddiesThanAvblWithColocDiffHosts()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_different_hosts.get(0));
      list.add(buddies_different_hosts.get(1));


      List results = getBuddies(3, true, list);

      assertEquals(3, results.size());
      assertEquals(buddies_different_hosts.get(0), results.get(0));
      assertEquals(buddies_different_hosts.get(1), results.get(1));
      assertEquals(buddies_localhost.get(0), results.get(2));
   }

   // now lets try this with a buddy pool
   public void testSingleLocalBuddyWithPool()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));

      Map<Address, String> pool = new HashMap<Address, String>();
      pool.put(dataOwner, "A");
      pool.put(buddies_localhost.get(2), "A");
      pool.put(buddies_localhost.get(0), "B");
      pool.put(buddies_localhost.get(1), "B");

      List results = getBuddies(1, true, list, pool);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(2), results.get(0));
   }


   // now lets try this with a buddy pool
   public void testSingleLocalBuddyWithPoolMixed1()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_different_hosts.get(0));
      list.add(buddies_different_hosts.get(1));

      Map<Address, String> pool = new HashMap<Address, String>();
      pool.put(dataOwner, "A");
      pool.put(buddies_localhost.get(2), "A");
      pool.put(buddies_localhost.get(0), "B");
      pool.put(buddies_localhost.get(1), "B");
      pool.put(buddies_different_hosts.get(0), "C");
      pool.put(buddies_different_hosts.get(1), "C");

      List results = getBuddies(1, true, list, pool);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(2), results.get(0));
   }

   public void testSingleLocalBuddyWithPoolMixed2()
   {
      List<Address> list = new LinkedList<Address>();
      list.add(dataOwner);
      list.add(buddies_localhost.get(0));
      list.add(buddies_localhost.get(1));
      list.add(buddies_localhost.get(2));
      list.add(buddies_different_hosts.get(0));
      list.add(buddies_different_hosts.get(1));

      Map<Address, String> pool = new HashMap<Address, String>();
      pool.put(dataOwner, "A");
      pool.put(buddies_localhost.get(2), "B");
      pool.put(buddies_localhost.get(0), "B");
      pool.put(buddies_localhost.get(1), "B");
      pool.put(buddies_different_hosts.get(0), "C");
      pool.put(buddies_different_hosts.get(1), "C");

      List results = getBuddies(1, true, list, pool);

      assertEquals(1, results.size());
      // preference should be for the non-coloc host
      assertEquals(buddies_different_hosts.get(0), results.get(0));
   }

   public void testWithDataOwnerAtEnd()
   {
      List<Address> list = new LinkedList<Address>();
      list.addAll(buddies_localhost);
      list.add(dataOwner);

      List results = getBuddies(1, true, list);

      assertEquals(1, results.size());
      assertEquals(buddies_localhost.get(0), results.get(0));
   }

}
