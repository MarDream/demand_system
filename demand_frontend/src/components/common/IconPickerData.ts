/**
 * Element Plus Icons 分类数据
 * 共 293 个图标，按功能分为 14 个类别
 */

export interface IconCategory {
  label: string
  icons: string[]
}

export const iconCategories: IconCategory[] = [
  {
    label: '方向箭头',
    icons: [
      'ArrowDown', 'ArrowDownBold', 'ArrowLeft', 'ArrowLeftBold',
      'ArrowRight', 'ArrowRightBold', 'ArrowUp', 'ArrowUpBold',
      'Bottom', 'BottomLeft', 'BottomRight', 'Top', 'TopLeft', 'TopRight',
      'Back', 'Right', 'DArrowLeft', 'DArrowRight', 'DCaret',
      'CaretBottom', 'CaretLeft', 'CaretRight', 'CaretTop',
      'Sort', 'SortDown', 'SortUp',
    ],
  },
  {
    label: '编辑操作',
    icons: [
      'Edit', 'EditPen', 'Delete', 'DeleteFilled', 'DeleteLocation',
      'CopyDocument', 'Crop', 'Scissor', 'Stamp', 'Brush', 'BrushFilled',
      'Check', 'Checked', 'Close', 'CloseBold', 'Plus', 'Minus', 'Expand', 'Fold',
      'ZoomIn', 'ZoomOut', 'FullScreen', 'Refresh', 'RefreshLeft', 'RefreshRight',
      'ScaleToOriginal', 'Filter', 'Switch', 'SwitchButton', 'SwitchFilled',
      'TurnOff', 'Open', 'Select', 'SemiSelect',
    ],
  },
  {
    label: '文档文件',
    icons: [
      'Document', 'DocumentAdd', 'DocumentChecked', 'DocumentCopy',
      'DocumentDelete', 'DocumentRemove', 'Folder', 'FolderAdd',
      'FolderChecked', 'FolderDelete', 'FolderOpened', 'FolderRemove',
      'Files', 'Notebook', 'Memo', 'Reading', 'ReadingLamp',
      'Download', 'Upload', 'UploadFilled', 'Paperclip', 'CopyDocument',
      'Printer', 'Postcard',
    ],
  },
  {
    label: '数据图表',
    icons: [
      'DataAnalysis', 'DataBoard', 'DataLine', 'TrendCharts',
      'Histogram', 'PieChart', 'Odometer', 'Rank',
      'Grid', 'List', 'Board',
    ],
  },
  {
    label: '界面布局',
    icons: [
      'Menu', 'Setting', 'SetUp', 'Operation', 'Management',
      'Guide', 'Layout', 'Platform', 'Monitor',
      'HomeFilled', 'House', 'School', 'OfficeBuilding',
    ],
  },
  {
    label: '用户角色',
    icons: [
      'User', 'UserFilled', 'Avatar', 'Female', 'Male',
      'Key', 'Lock', 'Unlock', 'View', 'Hide',
      'Iphone', 'Cellphone', 'Phone', 'PhoneFilled',
      'ChatDotRound', 'ChatDotSquare', 'ChatLineRound', 'ChatLineSquare',
      'ChatRound', 'ChatSquare', 'Comment', 'Message', 'MessageBox',
      'Notification', 'MuteNotification', 'Bell', 'BellFilled',
      'Mic', 'Microphone', 'Service', 'Headset',
    ],
  },
  {
    label: '地图位置',
    icons: [
      'Location', 'LocationFilled', 'LocationInformation',
      'MapLocation', 'Place', 'Position', 'Coordinate',
      'AddLocation', 'DeleteLocation', 'Compass', 'Guide',
    ],
  },
  {
    label: '状态提示',
    icons: [
      'InfoFilled', 'Warning', 'WarningFilled', 'WarnTriangleFilled',
      'SuccessFilled', 'Failed', 'CircleCheck', 'CircleCheckFilled',
      'CircleClose', 'CircleCloseFilled', 'QuestionFilled',
      'Help', 'HelpFilled', 'Opportunity',
    ],
  },
  {
    label: '时间日期',
    icons: [
      'Clock', 'AlarmClock', 'Timer', 'Stopwatch',
      'Calendar', 'Watch', 'QuartzWatch',
      'Sunny', 'Sunrise', 'Sunset', 'Moon', 'MoonNight',
    ],
  },
  {
    label: '交通出行',
    icons: [
      'Van', 'Ship', 'Bicycle', 'Football', 'Basketball',
      'Soccer', 'Baseball', 'Flag', 'Trophy', 'TrophyBase',
      'Medal', 'GoldMedal',
    ],
  },
  {
    label: '商品购物',
    icons: [
      'Goods', 'GoodsFilled', 'ShoppingBag', 'ShoppingCart',
      'ShoppingCartFull', 'ShoppingTrolley', 'Shop', 'Sell',
      'SoldOut', 'Present', 'PriceTag', 'Wallet', 'WalletFilled',
      'Money', 'CreditCard', 'Coin', 'Promotion', 'Discount',
      'Box', 'TakeawayBox',
    ],
  },
  {
    label: '美食饮品',
    icons: [
      'Coffee', 'CoffeeCup', 'Goblet', 'GobletFull',
      'GobletSquare', 'GobletSquareFull', 'Mug', 'MilkTea',
      'IceCream', 'IceCreamRound', 'IceCreamSquare',
      'IceDrink', 'IceTea', 'HotWater', 'ColdDrink',
      'Burger', 'Fries', 'Chicken', 'Dish', 'DishDot',
      'Food', 'Bowl', 'Dessert', 'ForkSpoon', 'KnifeFork',
      'Lollipop', 'Sugar', 'Apple', 'Pear', 'Grape',
      'Cherry', 'Watermelon', 'Orange',
    ],
  },
  {
    label: '天气自然',
    icons: [
      'Cloudy', 'PartlyCloudy', 'MostlyCloudy', 'Drizzling',
      'Sunny', 'Lightning', 'WindPower', 'Umbrella',
    ],
  },
  {
    label: '其他图标',
    icons: [
      'Star', 'StarFilled', 'Link', 'Connection',
      'Share', 'MagicStick', 'Magnet', 'Lightning',
      'Search', 'Aim', 'Pointer', 'Lollipop',
      'FirstAidKit', 'Tools', 'Pouring', 'Refrigerator',
      'Suitcase', 'SuitcaseLine', 'Briefcase', 'Handbag',
      'Pear', 'Picture', 'PictureFilled', 'PictureRounded',
      'VideoCamera', 'VideoCameraFilled', 'VideoPause', 'VideoPlay',
      'Film', 'Camera', 'CameraFilled', 'Headset',
      'Mouse', 'NoSmoking', 'Smoking', 'Mute',
      'ToiletPaper', 'Ticket', 'Tickets', 'Collection',
      'CollectionTag', 'ChromeFilled', 'ElementPlus',
      'Eleme', 'ElemeFilled', 'Finished',
      'Loading', 'More', 'MoreFilled',
      'AddLocation', 'CirclePlus', 'CirclePlusFilled', 'Remove', 'RemoveFilled',
    ],
  },
]

/** 获取所有图标名（去重） */
export function getAllIconNames(): string[] {
  const set = new Set<string>()
  iconCategories.forEach(c => c.icons.forEach(i => set.add(i)))
  return Array.from(set).sort()
}

/** 搜索图标 */
export function searchIcons(keyword: string): string[] {
  if (!keyword.trim()) return getAllIconNames()
  const lower = keyword.toLowerCase()
  return getAllIconNames().filter(name => name.toLowerCase().includes(lower))
}
