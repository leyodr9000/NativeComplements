"""Verify the supplied Pixel17/907 APK's exact Quickstep members (no proprietary APK bundled)."""
from pathlib import Path
import hashlib,json,sys,zipfile
import verify_signal_target as reader
P='Lcom/android/quickstep/'
METHODS={
 P+'TouchInteractionHandler;':{'getInputResource':'(I)Lcom/android/quickstep/TouchInteractionHandler$InputResource;','onInputEvent':'(Landroid/view/InputEvent;)V'},
 P+'GestureState;':{'getTopRunningTaskId':'()I','getRunningTask':'()Lcom/android/quickstep/TopTaskTracker$CachedTaskInfo;'},
 P+'TopTaskTracker$CachedTaskInfo;':{'getTaskId':'()I','getPackageName':'()Ljava/lang/String;','isHomeTask':'()Z'},
 P+'InputConsumer;':{'getActiveConsumerInHierarchy':'()Lcom/android/quickstep/InputConsumer;'},
 'Lcom/android/app/displaylib/PerDisplayRepository;':{'get':'(I)Ljava/lang/Object;'},
 P+'RecentsAnimationController;':{'finishController':'(ZLjava/lang/Runnable;ZLcom/android/quickstep/util/ActiveGestureLog$CompoundString;)V'},
}
FIELDS={
 P+'TouchInteractionService;':{'handler':P+'TouchInteractionHandler;'},
 P+'TouchInteractionHandler$InputResource;':{'gestureState':P+'GestureState;','consumer':P+'InputConsumer;'},
 P+'TouchInteractionHandler;':{'mTaskAnimationManagerRepository':'Lcom/android/app/displaylib/PerDisplayRepository;'},
 P+'TopTaskTracker$CachedTaskInfo;':{'mTopTask':'Landroid/app/TaskInfo;'},
 P+'TaskAnimationManager;':{'mController':P+'RecentsAnimationController;'},
 P+'AbsSwipeUpHandler;':{'mGestureStarted':'Z'},
 P+'inputconsumers/OtherActivityInputConsumer;':{'mGestureState':P+'GestureState;','mInteractionHandler':P+'AbsSwipeUpHandler;'},
}
def check(path):
 reader.TARGETS=set(METHODS)|set(FIELDS);classes={}
 with zipfile.ZipFile(path) as z:
  for n in z.namelist():
   if n.endswith('.dex'):classes.update(reader.Dex(z.read(n)).classes)
 for owner,members in METHODS.items():
  for name,descriptor in members.items():assert any(m['name']==name and m['descriptor']==descriptor for m in classes[owner]['methods']),(owner,name,descriptor)
 for owner,members in FIELDS.items():
  for name,t in members.items():assert any(f['name']==name and f['type']==t for f in classes[owner]['fields']),(owner,name,t)
 print('PASS exact supplied Pixel Quickstep methods and fields; cached top task is TaskInfo, not RunningTaskInfo')
 print('SHA256',hashlib.sha256(Path(path).read_bytes()).hexdigest())
if __name__=='__main__':check(Path(sys.argv[1]))
