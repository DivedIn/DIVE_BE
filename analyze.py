import re
import sys

file = sys.argv[1]
times = []
caller_runs = []
heap_usages = []
thread_counts = []

with open(file) as f:
    for line in f:
        match = re.search(
            r'videoId=(\d+), thread=([^,]+), 길이확인=(\d+)ms, 썸네일=(\d+)ms, STT=(\d+)ms, 피드백=(\d+)ms, 총=(\d+)ms, 힙=(\d+)MB, 전체스레드수=(\d+)',
            line
        )
        if match:
            video_id = match.group(1)
            thread = match.group(2)
            total_ms = int(match.group(7))
            heap = int(match.group(8))
            thread_count = int(match.group(9))
            times.append(total_ms)
            heap_usages.append(heap)
            thread_counts.append(thread_count)
            if 'http-nio' in thread:
                caller_runs.append(video_id)

if not times:
    print("측정 데이터 없음")
    sys.exit(1)

print(f"========== 결과 요약 ==========")
print(f"총 처리수:              {len(times)}개")
print(f"평균 처리시간:          {sum(times)/len(times):.0f}ms")
print(f"최소 처리시간:          {min(times):.0f}ms")
print(f"최대 처리시간:          {max(times):.0f}ms")
print(f"CallerRunsPolicy 발동:  {len(caller_runs)}개")
print(f"발동된 videoId:         {caller_runs}")
print(f"")
print(f"--- 리소스 사용량 ---")
print(f"평균 힙 메모리:         {sum(heap_usages)/len(heap_usages):.0f}MB")
print(f"최대 힙 메모리:         {max(heap_usages)}MB")
print(f"평균 스레드 수:         {sum(thread_counts)/len(thread_counts):.0f}개")
print(f"최대 스레드 수:         {max(thread_counts)}개")
print(f"================================")
