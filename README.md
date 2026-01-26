# TraceFlow

JAVA 어플리케이션 내의 메서드 호출 흐름도를 실시간 추적하여 시각화하는 플러그인입니다. </br>
최신버전을 기준으로 작성되어 있습니다.

#### ⚠️TraceFlow는 개발환경에서 사용을 권장합니다.
- 데이터를 네트워크 전송 받기 때문에 민감한 데이터 노출이 될 수 있습니다.
  - 자세한 데이터 내용은 [수집 데이터](#traceflow-수집-데이터)로 확인하시기 바랍니다.
- Jetty 서버를 추가로 띄우기때문에 성능 오버헤드 유발을 할수 있습니다.
  - [설정](#--초기-설정)에 따라 플러그인을 비활성화 할 수 있습니다.

## 1.주요기능
### 실시간 메서드 추적
- ByteBuddy기반 런타임 바이트코드 계측
- 메서드 진입 및 종료 시점 확인
- 실행 시간, 파라미터, 반환값, 예외 정보 수집

### 시각화 웹 UI
- 경량 Jetty 서버 및 D3.js를 사용한 웹 UI
- 메서드 흐름도의 시각화 및 상세 정보 확인
- UI 조작 및 Getter, Setter, 메서드 동일 계층 내의 중복 메서드 통합 필터링

[사진 1 첨부예정 - 트리구조]


[사진 2 첨부예정 - 클릭 시 반환값]


## 2.사용방법
#### - 초기 설정
```
plugins {
    id 'io.github.jth-00.traceflow' version '1.0.2'
}

traceFlow {
    packagePath = 'com.example.demo'  // 추적할 패키지 경로
    // webServerPort = 8081           // 웹 UI 포트 (선택사항, 기본값: 8081)
    // autoInject = false             // 플러그인 활성화 여부 (선택사항, 기본값: true)
}
```
#### - 이후 동작

플러그인 설정 이후 확인하려고하는 코드의 클래스 또는 메서드에 ``` @TraceFlow ```를 달아줍니다. </br>
 - ``` @TraceFlow ```가 달린 부분부터 이후의 메서드를 추적하며, 컨트롤러 및 서비스 레이어에서 다는 것을 추천합니다. </br>
 - public으로 선언된 클래스와 메서드만 ``` @TraceFlow ```가 적용이 되며, </br>
시작점이 되는 메서드와 이후 추적되는 하위 메서드들의 조건들은 [TraceFlow 제외 대상](#traceflow-추적-제외-대상)을 확인해 주시기바랍니다.

``` ./gradlew run ``` 또는 ``` ./gradlew bootRun ```실행을 한 뒤, Jetty서버 ``` localhost:8081(기본값) ```에 접속합니다.

해당 플러그인을 적용한 프로젝트의 api호출 시, Jetty 서버 내의 UI 새로고침 또는 새로고침 자동 활성화 이후 확인 가능합니다.

---

### TraceFlow 추적 제외 대상
#### - 최신버젼을 기준으로 제외되는 대상이며, ByteBuddy를 사용한 필터 조건입니다.

<details>
  <summary> TraceFlow 엔트리 메서드 </summary>
  
 #### 제외되는 패키지의 접미사
  
    [
        "net.bytebuddy",
        "java.", "javax.", "jakarta.",
        "sun.", "jdk.", "org.springframework",
        "org.hibernate", "com.mysql", "com.zaxxer",
        "io/github/jth00/traceflow"
    ]

#### 제외대상

```
   - 접근 제어자가 public이 아닌 것
   - 생성자 (isConstructor())
   - static (isStatic())
   - 컴파일러로 생성된 코드 (isSynthetic())
   - 추상 클래스 (isAbstract())
   
   // toString(), equals(Object obj), hashCode(), getClass() 등의 제외를 위해
   - Object.class에 선언된 메서드들 (isDeclaredBy(Object.class))
```

</details>

<details>
  <summary> 하위 메서드 </summary>

#### 제외되는 특정 단어를 포함하는 메서드
    [
        "$auxiliary$" // Bytebuddy의 보조클래스
        "$$", "CGLIB" // 프레임워크가 생성한 프록시/람다 클래스 제외
        "builder", "build" //빌더 패턴
        
        // Object.class에 선언된 메서드들, not(isDeclaredBy(Object.class))로 따로 뺄 예정
        "toString", "hashCode", "equals", "clone",
        "finalize", "getClass"
    ]

#### 제외되는 특정 단어로 시작하는 메서드

```
  "lambda$" //람다 클래스
  "access$" //컴파일러가 생성하는 브릿지 메서드
```

#### 제외대상

```
   - 생성자 (isConstructor())
   - static (isStatic())
   - 컴파일러로 생성된 코드 (isSynthetic())
   - 추상 클래스 (isAbstract())
   - 컴파일러가 생성하는 브릿지 메서드 (isBridge())
   - Java가 아닌 다른 언어로 구현된 메서드 (isNative())
   
   // 람다와 프록시등의 비지니스 로직과 관련이없는 메서드를 제외하기위해 추가하였지만, Inner 클래스도 제외되기에 수정될 예정
   - "$"를 포함하는 메서드 (isDeclaredBy(nameContains("$")))
```

</details>

### TraceFlow 수집 데이터

<details>
  <summary> 일반 데이터 </summary>
  
```
      {
          "className": "클래스 경로 및 클래스명", 
          "methodName": "메서드명",
          "returnType": "반환 타입명",
          "parameterTypes": "파라미터 타입명" ,
          "startTime": "실행시간",
          "duration": "실행 소요시간",
          "isAsync": "비동기여부(true,false)",
          "isError": "에러여부(true,false)",
      }
```  
</details>

<details>
  <summary> 에러 발생 시 </summary>
  
```
    {
        "className": "클래스 경로 및 클래스명", 
        "methodName": "메서드명",
        "returnType": "반환 타입명",
        "parameterTypes": "파라미터 타입명" ,
        "startTime": "실행시간",
        "duration": "실행 소요시간",
        "isAsync": "비동기여부(true,false)",
        "isError": "에러여부(true,false)",
        "errorType": "에러타입",
        "errorMessage": "에러 메세지",
        "stackTrace": "최대 5줄의 StackTrace 문장"
    }
```
</details>

---

### 연락처
문의사항 또는 개선사항이 있다면 dbzgtsa@gmail.com으로 메일 부탁드립니다.
