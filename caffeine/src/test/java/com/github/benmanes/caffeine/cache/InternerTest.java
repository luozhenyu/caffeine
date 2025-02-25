/*
 * Copyright 2022 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static com.github.benmanes.caffeine.cache.LocalCacheSubject.mapLocal;
import static com.github.benmanes.caffeine.cache.testing.CacheSubject.assertThat;
import static com.github.benmanes.caffeine.testing.MapSubject.assertThat;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import java.lang.ref.WeakReference;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.github.benmanes.caffeine.testing.Int;
import com.google.common.testing.GcFinalization;
import com.google.common.testing.NullPointerTester;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class InternerTest {

  @Test(dataProvider = "interners", expectedExceptions = NullPointerException.class)
  public void intern_null(Interner<Int> interner) {
    interner.intern(null);
  }

  @Test(dataProvider = "interners")
  public void intern(Interner<Int> interner) {
    var canonical = new Int(1);
    var other = new Int(1);

    assertThat(interner.intern(canonical)).isSameInstanceAs(canonical);
    assertThat(interner.intern(other)).isSameInstanceAs(canonical);
    checkSize(interner, 1);

    var next = new Int(2);
    assertThat(interner.intern(next)).isSameInstanceAs(next);
    checkSize(interner, 2);
    checkState(interner);
  }

  @Test
  public void intern_weak_replace() {
    var canonical = new Int(1);
    var other = new Int(1);

    Interner<Int> interner = Interner.newWeakInterner();
    assertThat(interner.intern(canonical)).isSameInstanceAs(canonical);

    var signal = new WeakReference<>(canonical);
    canonical = null;

    GcFinalization.awaitClear(signal);
    assertThat(interner.intern(other)).isSameInstanceAs(other);
    checkSize(interner, 1);
    checkState(interner);
  }

  @Test
  public void intern_weak_remove() {
    var canonical = new Int(1);
    var next = new Int(2);

    Interner<Int> interner = Interner.newWeakInterner();
    assertThat(interner.intern(canonical)).isSameInstanceAs(canonical);

    var signal = new WeakReference<>(canonical);
    canonical = null;

    GcFinalization.awaitClear(signal);
    assertThat(interner.intern(next)).isSameInstanceAs(next);
    checkSize(interner, 1);
    checkState(interner);
  }

  @Test
  public void intern_weak_cleanup() {
    var interner = (WeakInterner<Int>) Interner.<Int>newWeakInterner();
    interner.cache.drainStatus = BoundedLocalCache.REQUIRED;

    var canonical = new Int(1);
    interner.intern(canonical);
    assertThat(interner.cache.drainStatus).isEqualTo(BoundedLocalCache.IDLE);

    interner.cache.drainStatus = BoundedLocalCache.REQUIRED;
    interner.intern(canonical);
    assertThat(interner.cache.drainStatus).isEqualTo(BoundedLocalCache.IDLE);
  }

  @Test
  public void nullPointerExceptions() {
    new NullPointerTester().testAllPublicStaticMethods(Interner.class);
  }

  private void checkSize(Interner<Int> interner, int size) {
    if (interner instanceof StrongInterner) {
      assertThat(((StrongInterner<Int>) interner).map).hasSize(size);
    } else if (interner instanceof WeakInterner) {
      var cache = new LocalManualCache<Int, Boolean>() {
        @Override public LocalCache<Int, Boolean> cache() {
          return ((WeakInterner<Int>) interner).cache;
        }
        @Override public Policy<Int, Boolean> policy() {
          throw new UnsupportedOperationException();
        }
      };
      assertThat(cache).whenCleanedUp().hasSize(size);
    } else {
      Assert.fail();
    }
  }

  private void checkState(Interner<Int> interner) {
    if (interner instanceof WeakInterner) {
      assertAbout(mapLocal()).that(((WeakInterner<Int>) interner).cache).isValid();
    }
  }

  @DataProvider(name = "interners")
  Object[] providesInterners() {
    return new Object[] { Interner.newStrongInterner(), Interner.newWeakInterner() };
  }
}
