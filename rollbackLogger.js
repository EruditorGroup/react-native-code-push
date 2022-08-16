const startedMls = Date.now();
import {NativeModules, Platform, AppState} from 'react-native';

export function codepushRollbackLogger(value: string, mls: number){
  if (Platform.OS === 'android') {
    NativeModules.RNCodePushRollbackLogger.log(value, mls);
  }
};

function logJsStarted() {
  if (Platform.OS === 'android') {
    const isInBackground = AppState.currentState === 'background';
    NativeModules.RNCodePushRollbackLogger.log(
      isInBackground ? 'JS started (background)' : 'JS started',
      startedMls,
    );
  }
}

logJsStarted();
