import re, sys

file = sys.argv[1]
records = []

with open(file) as f:
    for line in f:
        m = re.search(
            r'videoId=(\d+), requestNo=(\d+), thread=([^,]+), threadType=([^,]+),'
            r' poolSize=(\d+), activeCount=(\d+),'
            r' acceptedAt=(\d+), completedAt=(\d+), duration=(\d+)ms, heap=(\d+)MB',
            line
        )
        if m:
            records.append({
                'video_id':     m.group(1),
                'request_no':   int(m.group(2)),
                'thread':       m.group(3),
                'thread_type':  m.group(4),   # 자바풀 or 톰캣
                'pool_size':    int(m.group(5)),
                'active':       int(m.group(6)),
                'accepted_at':  int(m.group(7)),
                'completed_at': int(m.group(8)),
                'duration':     int(m.group(9)),
                'heap':         int(m.group(10)),
            })

if not records:
    print('측정 데이터 없음'); sys.exit(1)

records.sort(key=lambda x: x['request_no'])  # 요청 번호 순 정렬

durations   = [r['duration'] for r in records]
tomcat_runs = [r for r in records if r['thread_type'] == '톰캣']

print(f'========== 결과 요약 ==========')
print(f'총 처리 완료:     {len(records)}개 / 100개')
print(f'평균 처리시간:    {sum(durations)/len(durations):.0f}ms')
print(f'최소 처리시간:    {min(durations)}ms')
print(f'최대 처리시간:    {max(durations)}ms')
print(f'톰캣 스레드 개입: {len(tomcat_runs)}건')
if tomcat_runs:
    print(f'톰캣 개입 시작:   {tomcat_runs[0]["request_no"]}번 요청')
print()
print(f'{"요청번호":<8} {"스레드종류":<10} {"생성스레드":<10} {"활성스레드":<10} {"처리시간":<12} {"힙(MB)"}')
for r in records:
    print(f'{r["request_no"]:<8} {r["thread_type"]:<10} {r["pool_size"]:<10} {r["active"]:<10} {str(r["duration"])+"ms":<12} {r["heap"]}')
print(f'================================')
