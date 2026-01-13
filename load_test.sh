echo "=== 옵션 2 부하 테스트 시작 ==="

# 70개 동시 요청
for i in {1..70}
do
    curl -X POST "http://localhost:8080/api/test/mock-video?processingMinutes=2" \
         -H "Content-Type: application/json" &
    echo "요청 $i 전송"
done

echo "70개 요청 전송 완료"
echo ""

# 5초 대기
sleep 5

echo "=== 시스템 상태 확인 ==="
curl -X GET "http://localhost:8080/api/test/status"
