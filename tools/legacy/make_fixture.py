"""Builds tools/legacy/fixture_v1.db: a v1.1 (Room version 2, table DiaryEntity) diary DB.

Usage: python3 tools/legacy/make_fixture.py
Rows cover: all 15 emoticons, null slots, duplicate keys, unknown resource id, picture-only,
unpadded month/day, a broken date, a non-null imageUri, Korean/multiline text.
"""
import os
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "fixture_v1.db")

E = {n: 0x7F100000 + i for i, n in enumerate(
    ["angry", "happy", "hot", "laughing", "like", "nervous", "picture", "sad",
     "shocked", "sick", "smile", "surprised", "thinking", "thumbsup", "tired"])}

ROWS = [
    # id, year, month, day, dow, e1, e2, e3, imageUri, title, content
    (1, "2024", "3", "5", "화", E["smile"], None, None, None, "봄", "날씨가 좋았다"),
    (2, "2024", "3", "6", "수", E["happy"], E["laughing"], E["like"], None, "친구", "엄마랑 카페\n두 줄"),
    (3, "2024", "12", "25", "수", E["angry"], E["hot"], E["nervous"], None, "", "제목 없음"),
    (4, "2024", "1", "1", "월", E["sad"], E["shocked"], E["sick"], None, "새해", "아팠다"),
    (5, "2023", "11", "30", "목", E["surprised"], E["thinking"], E["tired"], None, "t5", "c5"),
    (6, "2023", "7", "7", "금", E["thumbsup"], E["picture"], None, None, "t6", "c6"),
    (7, "2023", "7", "8", "토", E["picture"], None, None, None, "사진만", "c7"),
    (8, "2023", "7", "9", "일", None, None, None, None, "없음", "c8"),
    (9, "2023", "7", "10", "월", 2131099999, E["happy"], None, None, "모르는 id", "c9"),
    (10, "2023", "7", "11", "화", E["tired"], E["sick"], None, None, "중복", "tired+sick 둘 다 tired"),
    (11, "20x3", "", "11", "화", E["happy"], None, None, None, "깨진 날짜", "skip"),
    (12, "2024", "2", "29", "목", E["happy"], None, None, "content://media/picker/0/1", "uri", "c12"),
]


def main():
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    db.executescript("""
        CREATE TABLE android_metadata (locale TEXT);
        INSERT INTO android_metadata VALUES ('ko_KR');
        CREATE TABLE IF NOT EXISTS `DiaryEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `year` TEXT NOT NULL,
          `month` TEXT NOT NULL, `day` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `emoticonId1` INTEGER,
          `emoticonId2` INTEGER, `emoticonId3` INTEGER, `imageUri` TEXT, `title` TEXT NOT NULL, `content` TEXT NOT NULL);
        CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
        INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fixture');
        PRAGMA user_version = 2;
    """)
    db.executemany("INSERT INTO DiaryEntity VALUES (?,?,?,?,?,?,?,?,?,?,?)", ROWS)
    db.commit()
    db.close()
    print(f"wrote {OUT} ({len(ROWS)} rows)")


if __name__ == "__main__":
    main()
