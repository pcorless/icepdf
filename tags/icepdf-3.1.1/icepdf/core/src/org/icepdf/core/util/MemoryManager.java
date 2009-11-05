/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.util;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The <code>MemoryManager</code> class is a utility to help manage the amount of memory
 * available to the application.  When memory intensive operations are about
 * occur, the <code>MemoryManager</code> is asked if it can allocate the needed amount of memory.
 * If there is not enough memory available, the <code>MemoryManager</code> will purge the
 * cache to try and free the requested amount of memory.
 */
public class MemoryManager {

    private static final Logger logger =
            Logger.getLogger(MemoryManager.class.toString());

    // internal reference to MemeoryMangaer, used for singleton pattern.
    private static MemoryManager instance;

    /**
     * Runtime object responsible for returning VM memory use information
     */
    protected final Runtime runtime = Runtime.getRuntime();

    /**
     * The minimum amount of free memory at which the memory manager will force
     * a purge of the cached pageTree.  This value can be set by the system
     * property org.icepdf.core.minMemory
     */
    // Old default was 300000, but PageTree would set it to 3000000, to help
    //   ensure that parsing font glyphs will not result in a memory exception
    protected long minMemory = 5000000;

    /**
     * The maximum amount of memory allocated to the JVM.
     */
    protected long maxMemory;

    /**
     * When we decide to reduce our memory footprint, this is how many items we
     * purge at once.
     */
    protected int purgeSize;

    /**
     * If a memory-based ceiling, like maxMemory, is not sufficient, then you
     * can use maxSize to specify the maximum number of items that may
     * be opened before purging commences. A value of 0 (zero) means it
     * will not be used
     */
    protected int maxSize;

    protected WeakHashMap locked; // WeakHashMap< Object user, HashSet<MemoryManageable> >
    protected ArrayList leastRecentlyUsed; // ArrayList<MemoryManageable>

    protected long cumulativeDurationManagingMemory;
    protected long cumulativeDurationNotManagingMemory;
    protected long previousTimestampManagedMemory;
    protected int percentageDurationManagingMemory;

    protected ArrayList delegates; // ArrayList<MemoryManagerDelegates>

    /**
     * Sets an existing <code>MemoryManager</code> object to this
     * <code>MemoryManager</code> object.
     *
     * @param memoryMangaer <code>MemoryManager</code> object to point to.
     */
    public static void setInstance(MemoryManager memoryMangaer) {
        instance = memoryMangaer;
    }

    /**
     * Get an instance of the <code>MemoryManager</code>.  If there is not a <code>MemoryManager</code>
     * initiated, a new <code>MemoryManager</code> is is created and returned.
     *
     * @return the current <code>MemoryManager</code> object org.icepdf.core.minMemory
     */
    public static MemoryManager getInstance() {
        if (instance == null) {
            instance = new MemoryManager();
        }
        return instance;
    }

    /**
     * Creates a new instance of a <code>MemoryManager</code>.
     */
    protected MemoryManager() {
        // get min system meory
        try {
            int t = parse("org.icepdf.core.minMemory");
            if (t > 0) {
                minMemory = t;
            }
        }catch(Throwable e){
            logger.log(Level.FINE, "Error setting org.icepdf.core.minMemory");
        }

        // gen max memory of jdk
        maxMemory = Runtime.getRuntime().maxMemory();

        purgeSize = Defs.sysPropertyInt("org.icepdf.core.purgeSize", 5);

        maxSize = Defs.sysPropertyInt("org.icepdf.core.maxSize", 0);


        locked = new WeakHashMap();
        leastRecentlyUsed = new ArrayList(256);
        delegates = new ArrayList(64);
    }

    public synchronized void lock(Object user, MemoryManageable mm) {
        if (user == null || mm == null)
            return;
//System.out.println("+-+ MM.lock()    user: " + user + ", mm: " + mm);
        HashSet inUse = (HashSet) locked.get(user);
        if (inUse == null) {
            inUse = new HashSet(256);
            locked.put(user, inUse);
        }
        inUse.add(mm);

        leastRecentlyUsed.remove(mm);
        leastRecentlyUsed.add(mm);

        if (maxSize > 0) {
            int numUsed = leastRecentlyUsed.size();
            int numUsedMoreThanShould = numUsed - maxSize;
            if (numUsedMoreThanShould > 0) {
//System.out.println("+-+ MM.lock()      numUsedMoreThanShould: " + numUsedMoreThanShould + ", maxSize: " + maxSize + ", numUsed: " + numUsed);
                int numToDo = Math.max(purgeSize, numUsedMoreThanShould);
                int numDone = reduceMemory(numToDo);
            }
        }
    }

    public synchronized void release(Object user, MemoryManageable mm) {
        if (user == null || mm == null)
            return;
//System.out.println("+-+ MM.release() user: " + user + ", mm: " + mm);
        HashSet inUse = (HashSet) locked.get(user);
        if (inUse != null) {
            boolean removed = inUse.remove(mm);
            // remove locked reference if it no longer holds any mm objects.
            if (inUse.size() == 0) {
                locked.remove(user);
            }
//if( removed ) System.out.println("+-+ MM.release() mm was removed");
        }
    }

    public synchronized void registerMemoryManagerDelegate(MemoryManagerDelegate delegate) {
        if (!delegates.contains(delegate))
            delegates.add(delegate);
    }

    public synchronized void releaseAllByLibrary(Library library) {
//System.out.println("+-+ MM.releaseAllByLibrary() library: " + library);
        if (library == null)
            return;

        // Remove every MemoryManageable whose Library is library
        // Go in reverse order, so removals won't affect indexing
        for (int i = leastRecentlyUsed.size() - 1; i >= 0; i--) {
            MemoryManageable mm = (MemoryManageable) leastRecentlyUsed.get(i);
//System.out.println("+-+ MM.releaseAllByLibrary() LRU " + i + " of " + leastRecentlyUsed.size() + "  mm: " + mm);
            Library lib = mm.getLibrary();
            if (lib == null) {
//System.out.println("*** MM.releaseAllByLibrary() mm.getLibrary() was null");
                continue;
            }
//System.out.println("+-+ MM.releaseAllByLibrary() lib: " + lib + ",  lib == library (remove mm): " + lib.equals(library));
            if (lib.equals(library)) {
                leastRecentlyUsed.remove(i);
            }
        }

        // Go through every user, and looks at the MemoryManageable(s)
        //   that it's locking
        ArrayList usersToRemove = new ArrayList(); // ArrayList<Object>
        Set entries = locked.entrySet();
        Iterator entryIterator = entries.iterator();
        while (entryIterator.hasNext()) {
            Map.Entry entry = (Map.Entry) entryIterator.next();
            Object user = entry.getKey();
//System.out.println("+-+ MM.releaseAllByLibrary() user: " + user);
            HashSet inUse = (HashSet) entry.getValue();
            if (inUse != null) {
                // Remove every MemoryManageable whose Library is library
                ArrayList mmsToRemove = new ArrayList(); // ArrayList<MemoryManageable>
                Iterator mms = inUse.iterator();
                while (mms.hasNext()) {
                    MemoryManageable mm = (MemoryManageable) mms.next();
//System.out.println("+-+ MM.releaseAllByLibrary()   mm: " + mm);
                    if (mm != null) {
                        Library lib = mm.getLibrary();
                        if (lib == null) {
//System.out.println("*** MM.releaseAllByLibrary()   mm.getLibrary() was null");
                            continue;
                        }
//System.out.println("+-+ MM.releaseAllByLibrary()   lib: " + lib + ",  lib == library (remove mm): " + lib.equals(library));
                        if (lib.equals(library)) {
                            mmsToRemove.add(mm);
                        }
                    }
                }
                for (int i = 0; i < mmsToRemove.size(); i++)
                    inUse.remove(mmsToRemove.get(i));
                mmsToRemove.clear();

                // If the user has no more MemoryManageable(s)
                //   locked, then remove it as well.
                // We don't immediately remove it, since the
                //   iterators are fail-fast. Instead, mark them
                //   for removal, and do it after iterating
                if (inUse.size() == 0) {
//System.out.println("+-+ MM.releaseAllByLibrary() remove user: " + user);
                    usersToRemove.add(user);
                }
            }
        }
        for (int i = 0; i < usersToRemove.size(); i++)
            locked.remove(usersToRemove.get(i));
        usersToRemove.clear();

        for (int i = delegates.size() - 1; i >= 0; i--) {
            MemoryManagerDelegate mmd = (MemoryManagerDelegate) delegates.get(i);
            boolean shouldRemove = false;
            if (mmd == null)
                shouldRemove = true;
            else {
                Library lib = mmd.getLibrary();
                if (lib == null)
                    shouldRemove = true;
                else {
                    if (lib.equals(library))
                        shouldRemove = true;
                }
            }
            if (shouldRemove)
                delegates.remove(i);
        }
    }


    /**
     * @return If potentially reduced some memory
     */
    protected synchronized boolean reduceMemory() {
        int numToDo = purgeSize;

        int aggressive = 0;
        int lruSize = leastRecentlyUsed.size();
        if (percentageDurationManagingMemory > 15 || lruSize > 100)
            aggressive = lruSize * 60 / 100;
        else if (lruSize > 50)
            aggressive = lruSize * 50 / 100;
        else if (lruSize > 20)
            aggressive = lruSize * 40 / 100;
        if (aggressive > numToDo)
            numToDo = aggressive;

        int numDone = reduceMemory(numToDo);

        boolean delegatesReduced = false;
        if (numDone == 0)
            delegatesReduced = reduceMemoryWithDelegates(true);
        else if (numDone < numToDo)
            delegatesReduced = reduceMemoryWithDelegates(false);

        return ((numDone > 0) || delegatesReduced);
    }

    protected int reduceMemory(int numToDo) {
//System.out.println("+-+ MM.reduceMemory() numToDo: " + numToDo + ", LRU size: " + leastRecentlyUsed.size());
        int numDone = 0;
        try {
            int leastRecentlyUsedIndex = 0;
            while (numDone < numToDo && leastRecentlyUsedIndex < leastRecentlyUsed.size()) {
//System.out.println("+-+ MM.reduceMemory()   index: " + leastRecentlyUsedIndex + ", size: " + leastRecentlyUsed.size());
                MemoryManageable mm =
                        (MemoryManageable) leastRecentlyUsed.get(leastRecentlyUsedIndex);
//System.out.println("+-+ MM.reduceMemory()   isLocked: " + isLocked(mm) + ", mm: " + mm);
                if (!isLocked(mm)) {
                    mm.reduceMemory();
                    numDone++;
                    leastRecentlyUsed.remove(leastRecentlyUsedIndex);
                } else
                    leastRecentlyUsedIndex++;
            }
        }
        catch (Exception e) {
            logger.log(Level.FINE, "Problem while reducing memory",e);
        }
//System.out.println("+-+ MM.reduceMemory()   managing: " + cumulativeDurationManagingMemory + ", not: " + cumulativeDurationNotManagingMemory + "      managing: " + percentageDurationManagingMemory + "%");
        return numDone;
    }

    protected synchronized boolean isLocked(MemoryManageable mm) {
        Set entries = locked.entrySet();
        Iterator entryIterator = entries.iterator();
        // this can get pretty inefficient if locked is large
        while (entryIterator.hasNext()) {
            Map.Entry entry = (Map.Entry) entryIterator.next();
            HashSet inUse = (HashSet) entry.getValue();
            if (inUse != null && inUse.contains(mm))
                return true;
        }
        return false;
    }

    protected synchronized boolean reduceMemoryWithDelegates(boolean aggressively) {
        int reductionPolicy = aggressively ? MemoryManagerDelegate.REDUCE_AGGRESSIVELY
                : MemoryManagerDelegate.REDUCE_SOMEWHAT;
        boolean anyReduced = false;
        for (int i = 0; i < delegates.size(); i++) {
            MemoryManagerDelegate mmd = (MemoryManagerDelegate) delegates.get(i);
            if (mmd == null)
                continue;
            boolean reduced = mmd.reduceMemory(reductionPolicy);
            anyReduced |= reduced;
        }
        return anyReduced;
    }


    /**
     * Utility method to parse memory values specified by the k, K, m or M and
     * return the corresponding number of bytes.
     *
     * @param memoryValue memory value to parse
     * @return the number of bytes
     */
    private final static int parse(String memoryValue) {
        String s = Defs.sysProperty(memoryValue);
        if (s == null) {
            return -1;
        }
        int mult = 1;
        char c = s.charAt(s.length() - 1);
        if (c == 'k' || c == 'K') {
            mult = 1024;
            s = s.substring(0, s.length() - 1);
        }
        if (c == 'm' || c == 'M') {
            mult = 1024 * 1024;
            s = s.substring(0, s.length() - 1);
        }

        return mult * Integer.parseInt(s);
    }

    /**
     * Set the minimum amount of memory. Basically, if the amount
     * of free heap is under this value, the browser will go into
     * the error recovery mode.
     */
    public void setMinMemory(long m) {
        minMemory = m;
    }

    /**
     * Get the minimum amount of memory
     */
    public long getMinMemory() {
        return minMemory;
    }

    /**
     * Set the maximum amount of JVM heap. This should be the same
     * value as you give to jvm (normally with -mx parameter). The
     * <code>MemoryManager</code> will use this number to determine whether it
     * is low on memory. The manager also uses Runtime.freeMemory()
     * call but that call returns the free memory with only the
     * current heap size, not the maximum heap size.
     */
    public void setMaxMemory(long m) {
        maxMemory = m;
    }

    /**
     * Get maximum amount of jvm heap.
     */
    public long getMaxMemory() {
        return maxMemory;
    }

    /**
     * Get runtime free memory.
     *
     * @return free memory in bytes.
     */
    public long getFreeMemory() {
        return runtime.freeMemory();
    }

    /**
     * Will return true if the system is not in a low memory
     * condition after allocation of specified number of bytes.
     */
    private boolean canAllocate(int bytes, boolean doGC) {
        long mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }

        // Do we allow the heap to grow?
        long total = runtime.totalMemory();

        if (maxMemory > total) {
            mem += maxMemory - total;
            if ((mem - bytes) > minMemory) {
                return true;
            }
        }

        // This is so that checkMemory() can try to clear out
        //  some cached pages _before_ running the garbage collector,
        //  which saves a lot of CPU
        if (!doGC)
            return false;

        // Nope, try GC
        System.gc();
        mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }


        // Nope, try heavy GC
        System.runFinalization();
        System.gc();
        mem = runtime.freeMemory();

        // Enough memory?
        if ((mem - bytes) > minMemory) {
            return true;
        }

        // We are low on memory!
        //System.out.println("Failed -not enough Available Memory " + mem);
        return false;
    }

    /**
     * Check whether the runtime is low on memory. All well behaved
     * browser components (pilots and scripters) should call this
     * method before (during) attempts to allocate resources. If this
     * method returns true, such a component should stop its operation
     * and free up the memory it has allocated.
     */
    public boolean isLowMemory() {
        return !canAllocate(0, true);
    }

    /**
     * @param memoryNeeded
     * @return true if it's sure we can allocate memoryNeeded number of bytes
     */
    public boolean checkMemory(int memoryNeeded) {
        long beginTime = System.currentTimeMillis();
        int count = 0;
        // try and allocate memory, but quit after ten tries.
        while (!canAllocate((int) memoryNeeded, count > 0)) {
            // cache files on memory check
            boolean reducedSomething = reduceMemory();
            if (!reducedSomething && count > 0) {
                finishedMemoryProcessing(beginTime);
                return false;
            }
            count++;
            if (count > 10) {
                finishedMemoryProcessing(beginTime);
                return false;
            }
        }
        finishedMemoryProcessing(beginTime);
        return true;
    }

    private void finishedMemoryProcessing(long beginTime) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - beginTime;
        if (duration > 0)
            cumulativeDurationManagingMemory += duration;
        if (previousTimestampManagedMemory != 0) {
            duration = beginTime - previousTimestampManagedMemory;
            if (duration > 0)
                cumulativeDurationNotManagingMemory += duration;
        }
        previousTimestampManagedMemory = endTime;

        long totalDuration = cumulativeDurationManagingMemory + cumulativeDurationNotManagingMemory;
        if (totalDuration > 0) {
            percentageDurationManagingMemory =
                    (int) (cumulativeDurationManagingMemory * 100 / totalDuration);
        }
    }
}
