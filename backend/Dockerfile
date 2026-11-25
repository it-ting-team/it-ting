# 백엔드 실행 이미지.
# jar는 밖에서 먼저 만든다: backend/에서 ./gradlew bootJar → build/libs/app.jar
# 빌드(저장소 루트에서): docker build -t it-ting-backend backend
# 프로필과 접속 값은 실행할 때 환경 변수로 준다(deploy/test/compose.yaml). 이미지에 비밀값을 넣지 않는다.
FROM eclipse-temurin:25-jre

# root가 아닌 사용자로 실행한다.
# 올린 사진을 두는 폴더(ITTING_IMAGES_STORAGE_DIR)를 그 사용자의 것으로 미리 만든다.
# 여기에 볼륨을 붙이면 처음 만들어질 때 이 소유자를 물려받아, 서버가 파일을 쓸 수 있다.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin itting \
    && install -d -o itting -g itting /var/lib/itting/images
WORKDIR /app
COPY build/libs/app.jar app.jar
USER itting

# 힙은 컨테이너 메모리 상한의 70%까지 쓴다. 상한은 compose의 mem_limit가 정한다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
