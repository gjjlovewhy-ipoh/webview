from base.parser import Parser
import requests
from typing import Tuple, Any, Dict, Union, Iterable
import logging
import urllib.parse
import re
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
import time
from datetime import datetime
import threading

class Parser(Parser):  # 必须继承

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # 创建优化的session
        self.session = self._create_optimized_session()
        
        # 设置日志
        logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
        self.logger = logging.getLogger(__name__)
        self.logger.info("智能SOCKS5代理解析器初始化完成 - 支持简单URL和复杂授权URL")

    def _create_optimized_session(self) -> requests.Session:
        """创建优化的HTTP会话"""
        session = requests.Session()
        
        # 设置连接池和重试策略
        adapter = HTTPAdapter(
            pool_connections=20,
            pool_maxsize=50,
            max_retries=Retry(
                total=1,
                backoff_factor=0.1,
                status_forcelist=[500, 502, 503, 504]
            )
        )
        
        session.mount('http://', adapter)
        session.mount('https://', adapter)
        
        # 设置通用headers
        session.headers.update({
            'User-Agent': 'okhttp/3.15',
            'Accept': '*/*',
            'Accept-Encoding': 'identity',
            'Connection': 'keep-alive',
        })
        
        return session

    def _get_proxy_config(self, ip_port: str) -> Dict[str, str]:
        """根据 ip:port 构建 SOCKS5 代理配置"""
        if not ip_port:
            raise ValueError("代理IP和端口不能为空")
        
        proxy_url = f"socks5://{ip_port}"
        return {
            "http": proxy_url,
            "https": proxy_url
        }

    def _detect_url_type(self, url: str) -> str:
        """
        智能检测URL类型
        返回: 'simple' - 简单URL, 'complex' - 复杂授权URL
        """
        if not url:
            return 'simple'
        
        # 复杂URL的特征
        complex_indicators = [
            'accountinfo=',  # 包含账户信息
            'GuardEncType=', # 包含加密类型
            'PLTV/',         # IPTV路径格式
            '.smil',         # SMIL格式
            'fmt=ts2hls'     # 格式转换参数
        ]
        
        # 检查是否包含复杂URL特征
        for indicator in complex_indicators:
            if indicator in url:
                self.logger.info(f"检测到复杂授权URL: 包含 {indicator}")
                return 'complex'
        
        # 简单URL的特征
        simple_indicators = [
            'E=1&U=1&A=1&K=1&P=1&S=1',  # 简单参数组合
            'rxip.sc96655.com'           # 简单域名
        ]
        
        for indicator in simple_indicators:
            if indicator in url:
                self.logger.info(f"检测到简单URL: 包含 {indicator}")
                return 'simple'
        
        # 默认返回简单类型
        self.logger.info("未检测到特定类型，使用简单URL处理")
        return 'simple'

    def _sync_get_m3u8_status(self, url: str, proxy_config: Dict[str, str]) -> int:
        """同步请求M3U8 URL，返回状态码"""
        status_code = 0
        try:
            # 使用 HEAD 请求验证可达性
            response = self.session.head(url, proxies=proxy_config, timeout=3, verify=False, allow_redirects=True)
            status_code = response.status_code
            self.logger.info(f"M3U8状态码同步获取成功: {status_code}, URL: {url}")
        except requests.exceptions.Timeout:
            self.logger.warning(f"M3U8请求超时，无法获取状态码: {url}")
            status_code = 408
        except requests.exceptions.RequestException as e:
            self.logger.warning(f"M3U8请求错误，无法获取状态码: {e}")
            status_code = 599
        return status_code

    def parse(self, params: Dict[str, str]) -> Dict[str, str]:
        """
        智能解析参数并返回代理播放地址
        自动判断URL类型并采用相应处理策略
        """
        try:
            # 1. 获取代理IP和端口
            proxy_ip_port = params.get("ip", "").strip()
            if not proxy_ip_port:
                return {
                    "error": "缺少代理IP参数",
                    "usage": "请使用 ?ip=代理IP:端口&u=播放URL 的形式调用"
                }
            
            # 2. 获取播放URL参数 - 根据URL类型采用不同策略
            url_type = self._detect_url_type(params.get("u", ""))
            
            if url_type == 'complex':
                # 复杂URL处理策略
                play_url = self._extract_complex_url(params)
            else:
                # 简单URL处理策略
                play_url = self._extract_simple_url(params)
            
            if not play_url:
                return {
                    "error": "缺少播放URL参数",
                    "usage": "请使用 ?ip=代理IP:端口&u=播放URL 的形式调用"
                }
            
            # 3. 快速URL验证
            if not play_url.startswith(('http://', 'https://')):
                return {
                    "error": "无效的URL格式",
                    "message": "URL必须以 http:// 或 https:// 开头"
                }
            
            # 4. 检查回看参数
            playseek = params.get("playseek", "").strip()
            original_play_url = play_url
            
            if playseek:
                play_url = self._fast_process_playseek(play_url, playseek)
                self.logger.info(f"回看处理: {original_play_url} -> {play_url}")
            
            # 5. 同步获取 M3U8 状态码
            proxy_config = self._get_proxy_config(proxy_ip_port)
            status_code = self._sync_get_m3u8_status(original_play_url, proxy_config)
            
            # 6. 构建代理URL - 根据URL类型采用不同编码策略
            if url_type == 'complex':
                # 复杂URL需要特殊编码处理
                encoded_ip = urllib.parse.quote(proxy_ip_port, safe='')
                encoded_u = self._encode_complex_url(play_url)
                proxy_play_url = f"{self.address}?ip={encoded_ip}&u={encoded_u}"
            else:
                # 简单URL使用标准编码
                encoded_ip = urllib.parse.quote(proxy_ip_port, safe='')
                encoded_u = urllib.parse.quote(play_url, safe='')
                proxy_play_url = f"{self.address}?ip={encoded_ip}&u={encoded_u}"
            
            # 如果存在回看参数，也添加到代理URL中
            if playseek:
                proxy_play_url += f"&playseek={urllib.parse.quote(playseek, safe='')}"
            
            # 7. 返回结果
            return {
                "url": proxy_play_url,
                "proxy_server": proxy_ip_port,
                "status": "ready",
                "url_type": url_type,
                "m3u8_url": play_url,
                "original_m3u8_url": original_play_url,
                "playseek": playseek if playseek else "无",
                "m3u8_status_code": status_code
            }
            
        except Exception as e:
            self.logger.error(f"解析参数时出错: {e}")
            return {
                "error": f"解析失败: {str(e)}",
                "m3u8_status_code": 500
            }

    def _extract_simple_url(self, params: Dict[str, str]) -> str:
        """提取简单URL"""
        try:
            # 直接获取u参数
            play_url = params.get("u", "").strip()
            
            if play_url and play_url.startswith(('http://', 'https://')):
                # 检查是否需要合并其他参数
                if '?' in play_url and '&u=' not in play_url:
                    base_url, existing_params = play_url.split('?', 1)
                    additional_params = []
                    
                    for key, value in params.items():
                        if key not in ['u', 'ip', 'playseek'] and not key.startswith('_'):
                            additional_params.append(f"{key}={value}")
                    
                    if additional_params:
                        all_params = [existing_params] + additional_params
                        play_url = base_url + '?' + '&'.join(all_params)
                
                return play_url
            
            # 尝试从原始查询字符串重建
            if hasattr(self, 'raw_query_string'):
                raw_query = getattr(self, 'raw_query_string', '')
                if raw_query:
                    return self._reconstruct_simple_url(raw_query)
            
            return self._reconstruct_url_from_all_params(params)
            
        except Exception as e:
            self.logger.warning(f"提取简单URL时出错: {e}")
            return ""

    def _extract_complex_url(self, params: Dict[str, str]) -> str:
        """提取复杂授权URL"""
        try:
            # 首先尝试直接获取u参数
            play_url = params.get("u", "").strip()
            
            # 检查URL是否完整（包含accountinfo和GuardEncType）
            if play_url and 'accountinfo=' in play_url and 'GuardEncType=' in play_url:
                self.logger.info("检测到完整的复杂授权URL")
                return play_url
            
            # 如果URL不完整，尝试重建
            if play_url and not play_url.startswith('http'):
                try:
                    play_url = urllib.parse.unquote(play_url)
                except:
                    pass
            
            # 检查重建后的URL是否完整
            if play_url and 'accountinfo=' in play_url and 'GuardEncType=' in play_url:
                return play_url
            
            # 如果仍然不完整，尝试从其他参数重建完整URL
            return self._rebuild_complex_url(params)
            
        except Exception as e:
            self.logger.warning(f"提取复杂URL时出错: {e}")
            return ""

    def _reconstruct_simple_url(self, raw_query: str) -> str:
        """从原始查询字符串重建简单URL"""
        try:
            params_dict = urllib.parse.parse_qs(raw_query)
            u_values = params_dict.get('u', [])
            if u_values:
                base_url = u_values[0]
                additional_params = []
                for key, values in params_dict.items():
                    if key not in ['u', 'ip', 'playseek'] and values:
                        additional_params.append(f"{key}={values[0]}")
                
                if additional_params:
                    separator = '&' if '?' in base_url else '?'
                    return base_url + separator + '&'.join(additional_params)
                else:
                    return base_url
            
            return ""
        except Exception as e:
            self.logger.warning(f"重建简单URL时出错: {e}")
            return ""

    def _rebuild_complex_url(self, params: Dict[str, str]) -> str:
        """重建复杂授权URL"""
        try:
            base_url = params.get("u", "")
            if not base_url:
                return ""
            
            # 解码基础URL
            try:
                base_url = urllib.parse.unquote(base_url)
            except:
                pass
            
            # 收集其他可能属于URL的参数
            url_parts = [base_url]
            
            # 这些参数可能属于URL的一部分
            url_param_names = ['accountinfo', 'GuardEncType', 'fmt']
            
            for param_name in url_param_names:
                if param_name in params:
                    param_value = params[param_name]
                    # 解码参数值
                    try:
                        param_value = urllib.parse.unquote(param_value)
                    except:
                        pass
                    
                    # 对于accountinfo参数，确保+号正确保留
                    if param_name == 'accountinfo':
                        # 在未编码的URL中，+号应该被正确传递
                        pass
                    
                    url_parts.append(f"&{param_name}={param_value}")
            
            # 组合完整的URL
            complete_url = ''.join(url_parts)
            
            # 验证URL格式
            if complete_url.startswith(('http://', 'https://')):
                self.logger.info(f"重建后的复杂URL: {complete_url}")
                return complete_url
            
            return ""
            
        except Exception as e:
            self.logger.error(f"重建复杂URL失败: {e}")
            return ""

    def _reconstruct_url_from_all_params(self, params: Dict[str, str]) -> str:
        """从所有参数重建URL"""
        try:
            url_parts = []
            param_parts = []
            
            for key, value in params.items():
                if key == 'u':
                    url_parts.insert(0, value)
                elif key not in ['ip', 'playseek'] and not key.startswith('_'):
                    param_parts.append(f"{key}={value}")
            
            if url_parts:
                base_url = url_parts[0]
                if param_parts:
                    separator = '&' if '?' in base_url else '?'
                    return base_url + separator + '&'.join(param_parts)
                else:
                    return base_url
            
            return ""
        except Exception as e:
            self.logger.warning(f"从所有参数重建URL时出错: {e}")
            return ""

    def _encode_complex_url(self, url: str) -> str:
        """
        编码复杂URL - 确保+号被正确编码为%2B
        """
        # 先进行标准URL编码
        encoded = urllib.parse.quote(url, safe='')
        
        # 关键修复：将编码后的空格(%20)替换回加号(%2B)
        encoded = encoded.replace('%20', '%2B')
        
        self.logger.debug(f"复杂URL编码前: {url}")
        self.logger.debug(f"复杂URL编码后: {encoded}")
        
        return encoded

    def proxy(self, url: str, headers: Dict[str, Any]) -> Tuple[Union[bytes, Iterable[bytes]], Dict[str, str]]:
        """
        智能代理方法 - 自动判断URL类型并采用相应处理策略
        """
        start_time = time.time()
        
        try:
            # 1. 解析URL参数
            parsed_url = urllib.parse.urlparse(url)
            query_params = urllib.parse.parse_qs(parsed_url.query)
            
            # 2. 获取目标 URL
            target_url = self._extract_target_url(parsed_url.query, query_params)
            
            if not target_url:
                return self._quick_error_response("缺少URL参数u")
            
            # 3. 检测URL类型
            url_type = self._detect_url_type(target_url)
            self.logger.info(f"代理请求URL类型: {url_type}")
            
            # 4. 获取代理 IP
            proxy_ip_port = query_params.get('ip', [''])[0]
            if not proxy_ip_port:
                return self._quick_error_response("缺少代理IP参数ip")
            
            proxy_config = self._get_proxy_config(proxy_ip_port)

            # 5. 检查是否有独立的playseek参数需要处理
            playseek = query_params.get('playseek', [''])[0]
            if playseek and 'playseek=' not in target_url:
                separator = '&' if '?' in target_url else '?'
                target_url += f"{separator}playseek={playseek}"
                self.logger.info(f"添加回看参数到目标URL: {target_url}")

            # 6. 判断请求类型并设置超时
            is_m3u8 = self._is_m3u8_request(target_url, headers)
            timeout = 3 if is_m3u8 else 8
            
            # 7. 记录请求信息
            self.logger.info(f"代理请求: {target_url}, 类型: {url_type}, 代理: {proxy_ip_port}")
            
            # 8. 直接请求目标URL
            response = self.session.get(
                target_url,
                proxies=proxy_config,
                timeout=timeout,
                verify=False,
                stream=not is_m3u8
            )
            
            if response.status_code != 200:
                return self._quick_error_response(f"请求失败: {response.status_code}")
            
            # 9. 智能处理响应
            if is_m3u8:
                # 根据URL类型采用不同的m3u8处理策略
                if url_type == 'complex':
                    content = self._process_complex_m3u8(response.text, target_url, proxy_ip_port)
                else:
                    content = self._process_simple_m3u8(response.text, target_url, proxy_ip_port)
                
                content_bytes = content.encode('utf-8')
                
                response_headers = {
                    'Content-Type': 'application/vnd.apple.mpegurl',
                    'Content-Length': str(len(content_bytes)),
                    'Access-Control-Allow-Origin': '*',
                    'Cache-Control': 'no-cache, max-age=0',
                    'X-Processing-Time': f'{time.time() - start_time:.2f}s'
                }
            else:
                # TS片段直接返回
                content_bytes = response.content
                response_headers = {
                    'Content-Type': response.headers.get('Content-Type', 'video/mp2t'),
                    'Content-Length': response.headers.get('Content-Length', str(len(content_bytes))),
                    'Access-Control-Allow-Origin': '*',
                    'Cache-Control': 'public, max-age=7200',
                    'X-Processing-Time': f'{time.time() - start_time:.2f}s'
                }
            
            total_time = time.time() - start_time
            if total_time > 2.0:
                self.logger.info(f"代理完成: {len(content_bytes)} bytes, 耗时: {total_time:.2f}s, 类型: {url_type}")
            
            return content_bytes, response_headers
            
        except requests.exceptions.Timeout:
            return self._quick_error_response("请求超时，请检查网络或源地址")
        except requests.exceptions.ConnectionError:
            return self._quick_error_response("连接失败，请检查代理设置或代理是否可用")
        except Exception as e:
            return self._quick_error_response(f"代理错误: {str(e)}")

    def _extract_target_url(self, query_string: str, query_params: Dict) -> str:
        """从查询参数中提取目标URL，智能处理编码"""
        try:
            # 方法1: 直接获取u参数
            u_values = query_params.get('u', [])
            if u_values:
                target_url = u_values[0]
                
                # 检测URL类型
                url_type = self._detect_url_type(target_url)
                
                # 根据URL类型进行解码
                if url_type == 'complex':
                    # 复杂URL需要特殊解码
                    target_url = target_url.replace('%2B', '+')
                    target_url = urllib.parse.unquote(target_url)
                else:
                    # 简单URL正常解码
                    target_url = urllib.parse.unquote(target_url)
                
                # 检查是否有其他参数需要合并
                additional_params = []
                for key, values in query_params.items():
                    if key not in ['u', 'ip'] and values:
                        additional_params.append(f"{key}={values[0]}")
                
                if additional_params:
                    separator = '&' if '?' in target_url else '?'
                    return target_url + separator + '&'.join(additional_params)
                else:
                    return target_url
            
            # 方法2: 手动解析查询字符串
            parts = query_string.split('&')
            url_found = False
            base_url = ""
            other_params = []
            
            for part in parts:
                if '=' in part:
                    key, value = part.split('=', 1)
                    if key == 'u' and not url_found:
                        base_url = value
                        url_found = True
                    elif key != 'ip':
                        other_params.append(part)
                elif not url_found:
                    base_url = part
                    url_found = True
            
            if base_url and base_url.startswith(('http://', 'https://')):
                # 检测URL类型并解码
                url_type = self._detect_url_type(base_url)
                if url_type == 'complex':
                    base_url = base_url.replace('%2B', '+')
                    base_url = urllib.parse.unquote(base_url)
                else:
                    base_url = urllib.parse.unquote(base_url)
                
                if other_params:
                    separator = '&' if '?' in base_url else '?'
                    return base_url + separator + '&'.join(other_params)
                else:
                    return base_url
            
            return ""
            
        except Exception as e:
            self.logger.warning(f"提取目标URL时出错: {e}")
            return ""

    def _process_simple_m3u8(self, content: str, base_url: str, proxy_ip_port: str) -> str:
        """处理简单URL的m3u8内容"""
        if not content:
            return content
            
        lines = content.splitlines()
        processed_lines = []
        
        base_dir = base_url.rsplit('/', 1)[0] + '/' if '/' in base_url else base_url
        encoded_ip = urllib.parse.quote(proxy_ip_port, safe='')
        
        for line in lines:
            if not line or line.startswith('#'):
                processed_lines.append(line)
                continue
                
            if line.startswith(('http://', 'https://')):
                # 完整URL直接代理
                proxy_url = f"{self.address}?ip={encoded_ip}&u={urllib.parse.quote(line, safe='')}"
                processed_lines.append(proxy_url)
            else:
                # 相对路径转换为绝对路径
                if line.startswith('/'):
                    parsed = urllib.parse.urlparse(base_url)
                    full_url = f"{parsed.scheme}://{parsed.netloc}{line}"
                else:
                    full_url = urllib.parse.urljoin(base_dir, line)
                
                proxy_url = f"{self.address}?ip={encoded_ip}&u={urllib.parse.quote(full_url, safe='')}"
                processed_lines.append(proxy_url)
        
        return '\n'.join(processed_lines)

    def _process_complex_m3u8(self, content: str, base_url: str, proxy_ip_port: str) -> str:
        """处理复杂URL的m3u8内容 - 确保+号正确编码"""
        if not content:
            return content
            
        lines = content.splitlines()
        processed_lines = []
        
        base_dir = base_url.rsplit('/', 1)[0] + '/' if '/' in base_url else base_url
        encoded_ip = urllib.parse.quote(proxy_ip_port, safe='')
        
        for line in lines:
            if not line or line.startswith('#'):
                processed_lines.append(line)
                continue
                
            if line.startswith(('http://', 'https://')):
                # 完整URL - 使用复杂URL编码
                encoded_line = self._encode_complex_url(line)
                proxy_url = f"{self.address}?ip={encoded_ip}&u={encoded_line}"
                processed_lines.append(proxy_url)
            else:
                # 相对路径转换为绝对路径
                if line.startswith('/'):
                    parsed = urllib.parse.urlparse(base_url)
                    full_url = f"{parsed.scheme}://{parsed.netloc}{line}"
                else:
                    full_url = urllib.parse.urljoin(base_dir, line)
                
                # 使用复杂URL编码
                encoded_full_url = self._encode_complex_url(full_url)
                proxy_url = f"{self.address}?ip={encoded_ip}&u={encoded_full_url}"
                processed_lines.append(proxy_url)
        
        return '\n'.join(processed_lines)

    def _fast_process_playseek(self, play_url: str, playseek: str) -> str:
        """快速处理回看参数"""
        try:
            if '-' not in playseek:
                return play_url
                
            start_time_str, end_time_str = playseek.split('-', 1)
            start_time = self._fast_parse_time(start_time_str)
            end_time = self._fast_parse_time(end_time_str)
            
            if start_time and end_time:
                separator = '&' if '?' in play_url else '?'
                play_url += f"{separator}playseek={start_time}-{end_time}"
                
                self.logger.info(f"回看参数已添加: start={start_time}, end={end_time}")
            
            return play_url
            
        except Exception as e:
            self.logger.warning(f"快速处理回看参数时出错: {e}")
            return play_url

    def _fast_parse_time(self, time_str: str) -> str:
        """快速解析时间参数"""
        try:
            clean_str = time_str.strip()
            if clean_str.startswith('${(') and clean_str.endswith(')}'):
                clean_str = clean_str[3:-2]
            
            if clean_str.isdigit():
                if len(clean_str) == 13:
                    return clean_str
                elif len(clean_str) == 10:
                    return f"{clean_str}000"
            
            if '|' in clean_str:
                format_part, timezone = clean_str.split('|', 1)
                return self._format_current_time(format_part)
            else:
                return self._format_current_time(clean_str)
                
        except Exception:
            return str(int(time.time() * 1000))

    def _format_current_time(self, format_str: str) -> str:
        """格式化当前时间"""
        try:
            format_mapping = {
                'yyyy': '%Y',
                'MM': '%m',
                'dd': '%d',
                'HH': '%H',
                'mm': '%M',
                'ss': '%S'
            }
            
            py_format = format_str
            for k, v in format_mapping.items():
                py_format = py_format.replace(k, v)
            
            now = datetime.now()
            return now.strftime(py_format)
            
        except Exception:
            return datetime.now().strftime('%Y%m%d%H%M%S')

    def _is_m3u8_request(self, url: str, headers: Dict[str, Any]) -> bool:
        """快速判断是否为m3u8请求"""
        return '.m3u8' in url.lower()

    def _quick_error_response(self, error_msg: str) -> Tuple[bytes, Dict[str, str]]:
        """快速错误响应"""
        error_bytes = error_msg.encode('utf-8')
        return error_bytes, {
            'Content-Type': 'text/plain',
            'Content-Length': str(len(error_bytes)),
            'Access-Control-Allow-Origin': '*'
        }

    def stop(self):
        """停止解析器"""
        if hasattr(self, 'session'):
            self.session.close()
        self.logger.info("智能SOCKS5代理解析器已停止")

    def __del__(self):
        """析构函数"""
        self.stop()