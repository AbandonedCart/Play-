#import <UIKit/UIKit.h>
#import <GameController/GameController.h>
#import "iCade-iOS/iCadeReaderView.h"

@interface EmulatorViewController : UIViewController <iCadeEventDelegate>

@property (nonatomic) iCadeReaderView* iCadeReader;
@property (nonatomic) GCController *gController __attribute__((weak_import));

@property NSString* imagePath;

@end
