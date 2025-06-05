# Kibo-RPC 完整四區域巡邏與物品識別教學

## 新增功能概述

這個版本是前面所有功能的整合，實現了完整的四區域自動巡邏系統。程式能夠依序訪問所有區域，在每個區域進行物品識別，並且加入了記憶體管理和資源釋放機制，使程式更加穩定和實用。

## 新增的程式碼解析

### 1. 區域座標與方向定義

```java
private final Point[] AREA_POINTS = {
    new Point(10.9d, -10.0000d, 5.195d),    // Area 1
    new Point(10.925d, -8.875d, 4.602d),    // Area 2
    new Point(10.925d, -7.925d, 4.60093d),   // Area 3
    new Point(10.766d, -6.852d, 4.945d)     // Area 4
};

private final Quaternion[] AREA_QUATERNIONS = {
    new Quaternion(0f, 0f, -0.707f, 0.707f),    // Area 1
    new Quaternion(0f, 0.707f, 0f, 0.707f),     // Area 2
    new Quaternion(0f, 0.707f, 0f, 0.707f),     // Area 3
    new Quaternion(0f, 0f, 1f, 0f)              // Area 4
};
```

**程式碼解析：**
- `AREA_POINTS` - 定義四個區域的精確座標位置
- `AREA_QUATERNIONS` - 定義每個區域對應的相機朝向
- 陣列索引對應區域編號（0 = Area 1, 1 = Area 2, 等等）

**座標系統說明：**
- 座標基於 ISS 的固定坐標系統
- 每個區域都有最佳的觀察位置和角度
- 方向設定確保相機能正確對準目標區域

### 2. 結果儲存變數初始化

```java
// Prepare the variables to store treasure item locations
String[] foundItems = new String[AREA_POINTS.length];
int[] itemCounts = new int[AREA_POINTS.length];
```

**程式碼解析：**
- `foundItems` - 儲存每個區域識別出的物品名稱
- `itemCounts` - 儲存每個區域對應物品的數量
- 陣列大小與區域數量相同，便於後續處理

### 3. 核心巡邏迴圈

```java
// Visit each area
for (int areaId = 0; areaId < AREA_POINTS.length; areaId++) {
    // Move to the current area
    api.moveTo(AREA_POINTS[areaId], AREA_QUATERNIONS[areaId], false);

    // Get a camera image
    Mat image = api.getMatNavCam();
    // Save the image for debugging
    api.saveMatImage(image, "area_" + (areaId + 1) + ".png");
```

**程式碼解析：**
- `for` 迴圈依序訪問四個區域
- `areaId` 從 0 到 3，對應 Area 1 到 Area 4
- 每個區域都會拍攝影像並以區域編號命名儲存

**檔案命名規則：**
- area_1.png, area_2.png, area_3.png, area_4.png
- 便於後續分析每個區域的影像品質

### 4. 改進的記憶體管理

```java
// Release resources
thresholdResult.release();

// Release resources
result.release();
rotatedTemplate.release();
resizedTemplate.release();
```

**程式碼解析：**
- 在每次處理完成後立即釋放 Mat 物件
- 避免記憶體洩漏和資源耗盡
- 提高程式執行的穩定性

**記憶體管理最佳實務：**
```java
// 在迴圈內及時釋放臨時變數
// 在方法結束前釋放主要變數
// 避免累積大量未釋放的 Mat 物件
```

### 5. 結果儲存與回報

```java
// Find the most matched template
int mostMatchTemplateNum = getMxIndex(numMatches);

// Store the results
foundItems[areaId] = TEMPLATE_NAMES[mostMatchTemplateNum];
itemCounts[areaId] = numMatches[mostMatchTemplateNum];

// Report what was found in this area
api.setAreaInfo(areaId + 1, foundItems[areaId], itemCounts[areaId]);

// Log the results for debugging
Log.i(TAG, "Area " + (areaId + 1) + ": Found " + itemCounts[areaId] + " of " + foundItems[areaId]);
```

**程式碼解析：**
- 即時儲存每個區域的識別結果
- 使用 `areaId + 1` 將陣列索引轉換為實際區域編號
- 同時記錄日誌便於除錯和監控

### 6. 區域間資源清理

```java
// Release resources
image.release();
undistortImg.release();
cameraMatrix.release();
cameraCoefficients.release();
ids.release();
for (Mat corner : corners) {
    corner.release();
}
```

**程式碼解析：**
- 在每個區域處理完成後釋放所有相關資源
- 特別注意釋放集合中的每個 Mat 物件
- 確保下一個區域有足夠的記憶體空間

### 7. 任務完成流程

```java
// Move to the astronaut for reporting
Point astronautPoint = new Point(11.143, -6.7607, 4.9654);
Quaternion astronautQuaternion = new Quaternion(0f, 0f, 0.707f, 0.707f);
api.moveTo(astronautPoint, astronautQuaternion, false);
api.reportRoundingCompletion();

// TODO: Implement logic to identify the target item from astronaut's information
// For now, let's assume the target is in the first area
// String targetItem = TEMPLATE_NAMES[0]; // Replace with actual target item identification

// Notify that we recognized the target item
api.notifyRecognitionItem();

// Take a snapshot of the target item
api.takeTargetItemSnapshot();
```

**程式碼解析：**
- 所有區域巡邏完成後移動到太空人位置
- 包含 TODO 註解標示待實現的功能
- 暫時跳過目標物品識別，直接完成任務

## 改進的輔助方法

### 1. 增強的圖像旋轉方法

```java
private Mat rotImg(Mat img, int angle) {
    // Get the center of the image
    org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2, img.rows() / 2);

    // Create a rotation matrix
    Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);

    // Create a new Mat object to hold the rotated image
    Mat rotatedImg = new Mat();

    // Rotate the image using the rotation matrix
    Imgproc.warpAffine(img, rotatedImg, rotMat, img.size());
    
    // Release resources
    rotMat.release();

    return rotatedImg;
}
```

**改進點：**
- 新增 `rotMat.release()` 釋放旋轉矩陣
- 避免在大量旋轉操作中累積記憶體使用

## 實際應用建議

### 1. 完整的錯誤處理機制

```java
private boolean processArea(int areaId) {
    try {
        // Move to area
        Result moveResult = api.moveTo(AREA_POINTS[areaId], AREA_QUATERNIONS[areaId], false);
        if (!moveResult.hasSucceeded()) {
            Log.e(TAG, "Failed to move to area " + (areaId + 1));
            return false;
        }
        
        // Get image
        Mat image = api.getMatNavCam();
        if (image == null || image.empty()) {
            Log.e(TAG, "Failed to capture image in area " + (areaId + 1));
            return false;
        }
        
        // Process image...
        return true;
        
    } catch (Exception e) {
        Log.e(TAG, "Error processing area " + (areaId + 1), e);
        return false;
    }
}
```

### 2. 動態參數調整

```java
private class AreaSpecificParams {
    public int widthMin, widthMax, changeWidth, changeAngle;
    public double threshold;
    
    public AreaSpecificParams(int area) {
        switch (area) {
            case 0: // Area 1 - 較遠距離
                widthMin = 15; widthMax = 60; threshold = 0.65;
                break;
            case 1: // Area 2 - 中距離
                widthMin = 20; widthMax = 80; threshold = 0.7;
                break;
            case 2: // Area 3 - 中距離
                widthMin = 20; widthMax = 80; threshold = 0.7;
                break;
            case 3: // Area 4 - 較近距離
                widthMin = 30; widthMax = 100; threshold = 0.75;
                break;
        }
        changeWidth = 3;
        changeAngle = 30;
    }
}
```

### 3. 結果驗證機制

```java
private boolean validateResults(String[] foundItems, int[] itemCounts) {
    for (int i = 0; i < foundItems.length; i++) {
        if (foundItems[i] == null || itemCounts[i] < 0) {
            Log.w(TAG, "Invalid result in area " + (i + 1));
            return false;
        }
        
        if (itemCounts[i] == 0) {
            Log.w(TAG, "No items found in area " + (i + 1));
        }
    }
    return true;
}
```

## 性能優化建議

### 1. 模板預處理

```java
private void preprocessTemplates(Mat[] templates) {
    for (int i = 0; i < templates.length; i++) {
        if (templates[i] != null) {
            // 正規化模板亮度
            Mat normalized = new Mat();
            Core.normalize(templates[i], normalized, 0, 255, Core.NORM_MINMAX);
            templates[i].release();
            templates[i] = normalized;
        }
    }
}
```

### 2. 平行處理考量

```java
// 注意：在 Astrobee 上不建議使用多線程
// 但可以考慮批次處理來優化性能
private int[] batchProcessTemplates(Mat targetImg, Mat[] templates, int startIdx, int endIdx) {
    int[] results = new int[endIdx - startIdx];
    
    for (int i = startIdx; i < endIdx; i++) {
        results[i - startIdx] = processTemplate(targetImg, templates[i]);
    }
    
    return results;
}
```