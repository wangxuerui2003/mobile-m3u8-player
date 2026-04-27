#!/usr/bin/env python3
"""
okzyw.net 资源获取模块
用于获取全部视频数据并存储到SQLite数据库
"""
import requests
import sqlite3
import json
import time
from typing import Dict, List, Optional, Tuple
from datetime import datetime
import os

API_BASE = "https://api.okzyw.net/api.php/provide/vod"
DB_FILE = "okzyw_data.db"


class OkzywFetcher:
    """okzyw.net 数据获取器"""
    
    def __init__(self, db_path: str = DB_FILE):
        self.db_path = db_path
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
    
    def get_list_page(self, page: int = 1) -> Dict:
        """获取列表页数据"""
        url = f"{API_BASE}/?ac=list&pg={page}"
        try:
            resp = self.session.get(url, timeout=30)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            print(f"获取第{page}页失败: {e}")
            return {}
    
    def get_detail(self, vod_id: int) -> Dict:
        """获取单个视频详情"""
        url = f"{API_BASE}/?ac=detail&ids={vod_id}"
        try:
            resp = self.session.get(url, timeout=30)
            resp.raise_for_status()
            data = resp.json()
            if data.get('list'):
                return data['list'][0]
            return {}
        except Exception as e:
            print(f"获取详情 {vod_id} 失败: {e}")
            return {}
    
    def get_all_list_data(self) -> List[Dict]:
        """获取所有列表数据"""
        print("开始获取列表数据...")
        
        # 先获取第一页，确定总页数
        first_page = self.get_list_page(1)
        if not first_page:
            raise Exception("无法获取第一页数据")
        
        total_pages = first_page.get('pagecount', 0)
        total_items = first_page.get('total', 0)
        
        print(f"总页数: {total_pages}, 总条数: {total_items}")
        
        all_data = []
        all_data.extend(first_page.get('list', []))
        
        # 获取剩余页面
        for page in range(2, total_pages + 1):
            if page % 100 == 0:
                print(f"进度: {page}/{total_pages} ({page/total_pages*100:.1f}%)")
            
            page_data = self.get_list_page(page)
            if page_data.get('list'):
                all_data.extend(page_data['list'])
            
            # 避免请求过快
            time.sleep(0.1)
        
        print(f"列表数据获取完成，共 {len(all_data)} 条")
        return all_data
    
    def get_all_detail_data(self, vod_ids: List[int]) -> List[Dict]:
        """批量获取详情数据"""
        print(f"开始获取详情数据，共 {len(vod_ids)} 条...")
        
        all_details = []
        failed_ids = []
        
        for idx, vod_id in enumerate(vod_ids):
            if idx % 1000 == 0:
                print(f"进度: {idx}/{len(vod_ids)} ({idx/len(vod_ids)*100:.1f}%)")
            
            detail = self.get_detail(vod_id)
            if detail:
                all_details.append(detail)
            else:
                failed_ids.append(vod_id)
            
            # 避免请求过快
            time.sleep(0.05)
        
        print(f"详情数据获取完成，成功 {len(all_details)} 条，失败 {len(failed_ids)} 条")
        
        if failed_ids:
            print(f"失败的ID: {failed_ids[:10]}...")
        
        return all_details


class OkzywDatabase:
    """okzyw.net 数据库管理"""
    
    def __init__(self, db_path: str = DB_FILE):
        self.db_path = db_path
        self.conn = None
    
    def connect(self):
        """连接数据库"""
        self.conn = sqlite3.connect(self.db_path)
        self.conn.row_factory = sqlite3.Row
    
    def close(self):
        """关闭数据库"""
        if self.conn:
            self.conn.close()
    
    def init_db(self):
        """初始化数据库表"""
        cursor = self.conn.cursor()
        
        # 视频列表表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS video_list (
                vod_id INTEGER PRIMARY KEY,
                vod_name TEXT,
                type_id INTEGER,
                type_name TEXT,
                vod_en TEXT,
                vod_time TEXT,
                vod_remarks TEXT,
                vod_play_from TEXT
            )
        ''')
        
        # 视频详情表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS video_detail (
                vod_id INTEGER PRIMARY KEY,
                vod_name TEXT,
                vod_pic TEXT,
                vod_actor TEXT,
                vod_director TEXT,
                vod_area TEXT,
                vod_year TEXT,
                vod_time TEXT,
                vod_play_from TEXT,
                vod_play_url TEXT,
                type_id INTEGER,
                type_name TEXT,
                vod_content TEXT
            )
        ''')
        
        # FTS5全文搜索表
        cursor.execute('''
            CREATE VIRTUAL TABLE IF NOT EXISTS video_fts USING fts5(
                vod_id,
                vod_name,
                vod_actor,
                vod_director,
                type_name
            )
        ''')
        
        # 分类表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS category (
                type_id INTEGER PRIMARY KEY,
                type_name TEXT
            )
        ''')
        
        # 元数据表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS metadata (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        ''')
        
        self.conn.commit()
    
    def insert_video_list(self, videos: List[Dict]):
        """批量插入列表数据"""
        cursor = self.conn.cursor()
        
        cursor.executemany('''
            INSERT OR REPLACE INTO video_list 
            (vod_id, vod_name, type_id, type_name, vod_en, vod_time, vod_remarks, vod_play_from)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', [
            (
                v.get('vod_id'),
                v.get('vod_name'),
                v.get('type_id'),
                v.get('type_name'),
                v.get('vod_en'),
                v.get('vod_time'),
                v.get('vod_remarks'),
                v.get('vod_play_from')
            )
            for v in videos
        ])
        
        self.conn.commit()
        print(f"插入列表数据 {len(videos)} 条")
    
    def insert_video_detail(self, details: List[Dict], list_data_map: Dict[int, Dict] = None):
        """批量插入详情数据"""
        cursor = self.conn.cursor()
        
        # 如果提供了列表数据映射，从中获取type_id
        def get_type_id(detail):
            if list_data_map and detail.get('vod_id') in list_data_map:
                return list_data_map[detail['vod_id']].get('type_id')
            return detail.get('type_id')
        
        cursor.executemany('''
            INSERT OR REPLACE INTO video_detail 
            (vod_id, vod_name, vod_pic, vod_actor, vod_director, vod_area, vod_year, 
             vod_time, vod_play_from, vod_play_url, type_id, type_name, vod_content)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', [
            (
                d.get('vod_id'),
                d.get('vod_name'),
                d.get('vod_pic'),
                d.get('vod_actor'),
                d.get('vod_director'),
                d.get('vod_area'),
                d.get('vod_year'),
                d.get('vod_time'),
                d.get('vod_play_from'),
                d.get('vod_play_url'),
                get_type_id(d),
                d.get('type_name'),
                d.get('vod_content')
            )
            for d in details
        ])
        
        self.conn.commit()
        print(f"插入详情数据 {len(details)} 条")
    
    def update_fts_index(self):
        """更新FTS搜索索引"""
        cursor = self.conn.cursor()
        
        # 清空旧索引
        cursor.execute('DELETE FROM video_fts')
        
        # 重建索引
        cursor.execute('''
            INSERT INTO video_fts (vod_id, vod_name, vod_actor, vod_director, type_name)
            SELECT vod_id, vod_name, vod_actor, vod_director, type_name
            FROM video_detail
        ''')
        
        self.conn.commit()
        print("FTS搜索索引更新完成")
    
    def insert_categories(self, categories: List[Dict]):
        """插入分类数据"""
        cursor = self.conn.cursor()
        
        cursor.executemany('''
            INSERT OR REPLACE INTO category (type_id, type_name)
            VALUES (?, ?)
        ''', [(c.get('type_id'), c.get('type_name')) for c in categories])
        
        self.conn.commit()
        print(f"插入分类数据 {len(categories)} 条")
    
    def set_metadata(self, key: str, value: str):
        """设置元数据"""
        cursor = self.conn.cursor()
        cursor.execute('''
            INSERT OR REPLACE INTO metadata (key, value) VALUES (?, ?)
        ''', (key, value))
        self.conn.commit()
    
    def get_metadata(self, key: str) -> Optional[str]:
        """获取元数据"""
        cursor = self.conn.cursor()
        cursor.execute('SELECT value FROM metadata WHERE key = ?', (key,))
        row = cursor.fetchone()
        return row['value'] if row else None
    
    def search(self, query: str, limit: int = 50) -> List[Dict]:
        """全文搜索"""
        cursor = self.conn.cursor()
        
        # 使用LIKE作为备选方案，FTS5可能需要特殊配置
        try:
            cursor.execute('''
                SELECT vd.* 
                FROM video_fts fts
                JOIN video_detail vd ON vd.vod_id = fts.vod_id
                WHERE video_fts MATCH ?
                LIMIT ?
            ''', (query, limit))
            
            results = [dict(row) for row in cursor.fetchall()]
            if results:
                return results
        except Exception as e:
            print(f"FTS搜索失败，使用LIKE搜索: {e}")
        
        # 备选方案：使用LIKE搜索
        cursor.execute('''
            SELECT * FROM video_detail
            WHERE vod_name LIKE ? 
               OR vod_actor LIKE ?
               OR vod_director LIKE ?
               OR type_name LIKE ?
            LIMIT ?
        ''', (f'%{query}%', f'%{query}%', f'%{query}%', f'%{query}%', limit))
        
        return [dict(row) for row in cursor.fetchall()]
    
    def get_video_detail(self, vod_id: int) -> Optional[Dict]:
        """获取视频详情"""
        cursor = self.conn.cursor()
        cursor.execute('SELECT * FROM video_detail WHERE vod_id = ?', (vod_id,))
        row = cursor.fetchone()
        return dict(row) if row else None
    
    def get_videos_by_category(self, type_id: int, page: int = 1, page_size: int = 20) -> List[Dict]:
        """按分类获取视频"""
        cursor = self.conn.cursor()
        offset = (page - 1) * page_size
        
        cursor.execute('''
            SELECT * FROM video_detail 
            WHERE type_id = ?
            ORDER BY vod_time DESC
            LIMIT ? OFFSET ?
        ''', (type_id, page_size, offset))
        
        return [dict(row) for row in cursor.fetchall()]
    
    def get_stats(self) -> Dict:
        """获取数据库统计信息"""
        cursor = self.conn.cursor()
        
        stats = {}
        
        cursor.execute('SELECT COUNT(*) as count FROM video_list')
        stats['list_count'] = cursor.fetchone()['count']
        
        cursor.execute('SELECT COUNT(*) as count FROM video_detail')
        stats['detail_count'] = cursor.fetchone()['count']
        
        cursor.execute('SELECT COUNT(*) as count FROM category')
        stats['category_count'] = cursor.fetchone()['count']
        
        # 数据库文件大小
        stats['db_size_mb'] = os.path.getsize(self.db_path) / 1024 / 1024 if os.path.exists(self.db_path) else 0
        
        # 最后更新时间
        last_update = self.get_metadata('last_update')
        stats['last_update'] = last_update
        
        return stats


def fetch_all_data(db_path: str = DB_FILE, fetch_details: bool = True):
    """获取并存储全部数据"""
    print("="*60)
    print("开始获取 okzyw.net 全部数据")
    print("="*60)
    
    fetcher = OkzywFetcher(db_path)
    db = OkzywDatabase(db_path)
    
    db.connect()
    db.init_db()
    
    try:
        # 1. 获取列表数据
        list_data = fetcher.get_all_list_data()
        
        # 2. 存储列表数据
        db.insert_video_list(list_data)
        
        # 3. 获取分类数据
        first_page = fetcher.get_list_page(1)
        categories = first_page.get('class', [])
        db.insert_categories(categories)
        
        # 4. 获取详情数据（如果需要）
        if fetch_details:
            vod_ids = [v['vod_id'] for v in list_data]
            detail_data = fetcher.get_all_detail_data(vod_ids)
            db.insert_video_detail(detail_data)
            db.update_fts_index()
        
        # 5. 更新元数据
        db.set_metadata('last_update', datetime.now().isoformat())
        db.set_metadata('total_items', str(len(list_data)))
        
        # 6. 输出统计信息
        stats = db.get_stats()
        print("\n" + "="*60)
        print("数据获取完成")
        print("="*60)
        print(f"列表数据: {stats['list_count']} 条")
        print(f"详情数据: {stats['detail_count']} 条")
        print(f"分类数据: {stats['category_count']} 条")
        print(f"数据库大小: {stats['db_size_mb']:.2f} MB")
        print(f"最后更新: {stats['last_update']}")
        print("="*60)
        
    finally:
        db.close()


def fetch_list_to_json(output_path: str, delay: float = 0.8, max_pages: int = 0):
    """获取列表数据并输出为JSON文件（用于Android APK预置数据）"""
    print("="*60)
    print("开始获取列表数据 → JSON")
    print("="*60)
    
    fetcher = OkzywFetcher()
    
    # 获取第一页
    first_page = fetcher.get_list_page(1)
    if not first_page:
        raise Exception("无法获取第一页数据")
    
    total_pages = first_page.get('pagecount', 0)
    total_items = first_page.get('total', 0)
    categories = first_page.get('class', [])
    
    # Limit pages if max_pages specified
    fetch_pages = min(total_pages, max_pages) if max_pages > 0 else total_pages
    
    print(f"总页数: {total_pages}, 总条数: {total_items}, 本次获取: {fetch_pages} 页")
    
    all_videos = list(first_page.get('list', []))
    print(f"第 1/{fetch_pages} 页, 已获取 {len(all_videos)} 条")
    
    # 获取剩余页面
    for page in range(2, fetch_pages + 1):
        time.sleep(delay)
        page_data = fetcher.get_list_page(page)
        videos = page_data.get('list', [])
        if not videos:
            print(f"第 {page}/{fetch_pages} 页为空，跳过")
            continue
        all_videos.extend(videos)
        if page % 10 == 0 or page == fetch_pages:
            print(f"第 {page}/{fetch_pages} 页, 已获取 {len(all_videos)} 条")
    
    # 构建输出
    output = {
        "categories": categories,
        "videos": all_videos,
        "total": total_items,
        "fetched_at": datetime.now().isoformat()
    }
    
    # 写入文件
    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else '.', exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False)
    
    file_size_mb = os.path.getsize(output_path) / 1024 / 1024
    print(f"\nJSON文件已写入: {output_path}")
    print(f"视频数: {len(all_videos)}, 分类数: {len(categories)}")
    print(f"文件大小: {file_size_mb:.2f} MB")
    print("="*60)


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='获取 okzyw.net 全部数据')
    subparsers = parser.add_subparsers(dest='command', help='子命令')
    
    # fetch-db: 原有功能，获取数据到SQLite
    db_parser = subparsers.add_parser('fetch-db', help='获取数据到SQLite数据库')
    db_parser.add_argument('--db', default=DB_FILE, help='数据库文件路径')
    db_parser.add_argument('--no-details', action='store_true', help='不获取详情数据')
    
    # fetch-json: 获取列表数据到JSON（用于APK预置）
    json_parser = subparsers.add_parser('fetch-json', help='获取列表数据到JSON文件（APK预置）')
    json_parser.add_argument('--output', default='app/src/main/assets/preset_videos.json', help='输出JSON路径')
    json_parser.add_argument('--delay', type=float, default=0.5, help='请求间隔(秒)')
    json_parser.add_argument('--max-pages', type=int, default=200, help='最大获取页数(0=全部)')
    
    args = parser.parse_args()
    
    if args.command == 'fetch-json':
        fetch_list_to_json(args.output, delay=args.delay, max_pages=args.max_pages)
    else:
        # 默认行为：fetch-db
        db_path = getattr(args, 'db', DB_FILE)
        no_details = getattr(args, 'no_details', False)
        fetch_all_data(db_path, fetch_details=not no_details)
