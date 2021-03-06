/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foo;

import androidx.lifecycle.GeneratedAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MethodCallsLogger;
import java.lang.Override;
import javax.annotation.Generated;

@Generated("androidx.lifecycle.LifecycleProcessor")
public class OnAnyMethod_LifecycleAdapter implements GeneratedAdapter {
  final OnAnyMethod mReceiver;

  OnAnyMethod_LifecycleAdapter(OnAnyMethod receiver) {
    this.mReceiver = receiver;
  }

  @Override
  public void callMethods(LifecycleOwner owner, Lifecycle.Event event, boolean onAny,
      MethodCallsLogger logger) {
    boolean hasLogger = logger != null;
    if (onAny) {
      if (!hasLogger || logger.approveCall("any", 2)) {
        mReceiver.any(owner);
      }
      if (!hasLogger || logger.approveCall("any", 4)) {
        mReceiver.any(owner,event);
      }
      return;
    }
    if (event == Lifecycle.Event.ON_STOP) {
      if (!hasLogger || logger.approveCall("onStop", 2)) {
        mReceiver.onStop(owner);
      }
      return;
    }
  }
}
