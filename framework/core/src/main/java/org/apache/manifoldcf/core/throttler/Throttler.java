/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.core.throttler;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;

/** A Throttler object creates a virtual pool of connections to resources
* whose access needs to be throttled in number, rate of use, and byte rate.
* This code is modeled on the code for distributed connection pools, and is intended
* to work in a similar manner.  Basically, a periodic assessment is done about what the
* local throttling parameters should be (on a per-pool basis), and the local throttling
* activities then adjust what they are doing based on the new parameters.  A service
* model is used to keep track of which pools have what clients working with them.
* This implementation has the advantage that:
* (1) Only local throttling ever takes place on a method-by-method basis, which makes
*   it possible to use throttling even in streams and background threads;
* (2) Throttling resources are apportioned fairly, on average, between all the various
*   cluster members, so it is unlikely that any persistent starvation conditions can
*   arise.
*/
public class Throttler
{
  public static final String _rcsid = "@(#)$Id$";

  /** The service type prefix for throttle pools */
  protected final static String serviceTypePrefix = "_THROTTLEPOOL_";
  
  /** Pool hash table. Keyed by throttle group name; value is Pool */
  protected final Map<String,Pool> poolHash = new HashMap<String,Pool>();

  /** Create a throttler instance.  Usually there will be one of these per connector
  * type that needs throttling.
  */
  public Throttler()
  {
  }
  
  /** Check if throttle group name is still valid.
  * This can be overridden, but right now nobody does it.
  */
  protected boolean isThrottleGroupValid(IThreadContext threadContext, String throttleGroupName)
    throws ManifoldCFException
  {
    return true;
  }
  
  /** Get permission to use a connection, which is described by the passed array of bin names.
  * The connection can be used multiple
  * times until the releaseConnectionPermission() method is called.
  *@param threadContext is the thread context.
  *@param throttleGroup is the throttle group.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param binNames is the set of bin names to throttle for, within the throttle group.
  *@param currentTime is the current time, in ms. since epoch.
  *@return the fetch throttler to use for fetches with the obtained connection.
  */
  public IFetchThrottler obtainConnectionPermission(IThreadContext threadContext, String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    // MHL
    return null;
  }
  
  /** Release permission to use a connection. This presumes that obtainConnectionPermission()
  * was called earlier in the same thread and was successful.
  *@param threadContext is the thread context.
  *@param throttleGroup is the throttle group name.
  *@param throttleSpec is the throttle specification to use for the throttle group,
  *@param currentTime is the current time, in ms. since epoch.
  */
  public void releaseConnectionPermission(IThreadContext threadContext, String throttleGroup,
    IThrottleSpec throttleSpec, String[] binNames, long currentTime)
    throws ManifoldCFException
  {
    // MHL
  }
  
  /** Poll periodically.
  */
  public void poll(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and notify everyone
    synchronized (poolHash)
    {
      Iterator<String> iter = poolHash.keySet().iterator();
      while (iter.hasNext())
      {
        String throttleGroup = iter.next();
        Pool p = poolHash.get(throttleGroup);
        if (isThrottleGroupValid(threadContext,throttleGroup))
          p.poll(threadContext);
        else
        {
          p.destroy(threadContext);
          iter.remove();
        }
      }
    }
  }
  
  /** Free unused resources.
  */
  public void freeUnusedResources(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (poolHash)
    {
      Iterator<Pool> iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = iter.next();
        p.freeUnusedResources(threadContext);
      }
    }
  }
  
  /** Shut down all throttlers and deregister them.
  */
  public void destroy(IThreadContext threadContext)
    throws ManifoldCFException
  {
    // Go through the whole pool and clean it out
    synchronized (poolHash)
    {
      Iterator<Pool> iter = poolHash.values().iterator();
      while (iter.hasNext())
      {
        Pool p = iter.next();
        p.destroy(threadContext);
        iter.remove();
      }
    }
  }

  // Protected methods and classes
  
  protected String buildServiceTypeName(String throttleGroupName)
  {
    return serviceTypePrefix + throttleGroupName;
  }
  

  /** This class represents a value in the pool hash, which corresponds to a given key.
  */
  protected class Pool
  {
    /** Whether this pool is alive */
    protected boolean isAlive = true;
    /** Service type name */
    protected final String serviceTypeName;
    /** The (anonymous) service name */
    protected final String serviceName;
    /** The current throttle spec */
    protected IThrottleSpec throttleSpec;
    
    /** Constructor
    */
    public Pool(IThreadContext threadContext, String throttleGroup, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.serviceTypeName = buildServiceTypeName(throttleGroup);
      this.throttleSpec = throttleSpec;
      // Now, register and activate service anonymously, and record the service name we get.
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      this.serviceName = lockManager.registerServiceBeginServiceActivity(serviceTypeName, null, null);
    }

    /** Update the throttle spec.
    *@param throttleSpec is the new throttle spec for this throttle group.
    */
    public synchronized void updateThrottleSpec(IThreadContext threadContext, IThrottleSpec throttleSpec)
      throws ManifoldCFException
    {
      this.throttleSpec = throttleSpec;
    }
    
    // MHL
    
    /** Call this periodically.
    */
    public synchronized void poll(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // MHL
    }
    
    /** Free unused resources.
    */
    public synchronized void freeUnusedResources(IThreadContext threadContext)
      throws ManifoldCFException
    {
      // MHL
    }
    
    /** Destroy this pool.
    */
    public synchronized void destroy(IThreadContext threadContext)
      throws ManifoldCFException
    {
      freeUnusedResources(threadContext);
      // End service activity
      isAlive = false;
      notifyAll();
      ILockManager lockManager = LockManagerFactory.make(threadContext);
      lockManager.endServiceActivity(serviceTypeName, serviceName);
    }
  }
  
}
