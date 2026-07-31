#!/usr/bin/env python3
"""
GitHub Actions 构建监控脚本
每 15 分钟检查构建状态，失败则自动修复，成功则暂停任务
任务名称：fb
"""

import os
import sys
import json
import subprocess
import urllib.request
import urllib.error
from datetime import datetime
from pathlib import Path

# 配置
REPO_OWNER = "lookmeplus-l"
REPO_NAME = "HPC"
WORKFLOW_FILE = "build-apk.yml"
STATE_FILE = Path(__file__).parent / "fb_monitor.state"
LOG_FILE = Path(__file__).parent / "fb_monitor.log"

def log(message):
    """记录日志"""
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    log_entry = f"[{timestamp}] {message}"
    print(log_entry)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(log_entry + "\n")

def get_github_token():
    """获取 GitHub Token（优先环境变量，其次 Git 凭据）"""
    token = os.getenv("GITHUB_TOKEN")
    if token:
        return token

    token = os.getenv("MONKEYCODE_GITHUB_ACCESS_TOKEN")
    if token:
        return token

    token = get_token_from_git()
    if token:
        log("已从 Git 凭据获取 Token")
        return token

    log("错误：无法获取 GitHub Token")
    sys.exit(1)

def get_token_from_git():
    """通过 git credential helper 获取 GitHub Token"""
    try:
        result = subprocess.run(
            ["git", "credential", "fill"],
            input="protocol=https\nhost=github.com\n\n",
            capture_output=True,
            text=True,
            timeout=15,
            cwd=Path(__file__).parent.parent
        )
        if result.returncode == 0:
            for line in result.stdout.splitlines():
                if line.startswith("password="):
                    return line.split("=", 1)[1].strip()
    except Exception:
        pass
    return None

def check_workflow_status():
    """检查最新的工作流运行状态"""
    token = get_github_token()
    url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/actions/workflows/{WORKFLOW_FILE}/runs?per_page=1"
    
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read().decode())
            if not data.get("workflow_runs"):
                log("未找到任何运行记录")
                return "no_runs"
            
            latest_run = data["workflow_runs"][0]
            status = latest_run.get("status")
            conclusion = latest_run.get("conclusion")
            
            log(f"最新构建：status={status}, conclusion={conclusion}")
            log(f"运行 URL: {latest_run.get('html_url')}")
            
            if status == "completed":
                return conclusion
            else:
                return "in_progress"
    except urllib.error.HTTPError as e:
        log(f"API 请求失败：{e.code} {e.reason}")
        return "error"
    except Exception as e:
        log(f"检查失败：{e}")
        return "error"

def parse_build_log(output):
    """解析构建日志，识别常见错误"""
    errors = []
    
    if "401 Unauthorized" in output or "403 Forbidden" in output:
        errors.append("auth_error")
    
    if "SDK location not found" in output:
        errors.append("sdk_missing")
    
    if "JAVA_HOME is not set" in output:
        errors.append("java_missing")
    
    if "Execution failed" in output:
        if ":app:processDebugResources" in output:
            errors.append("resource_error")
        if "minSdk" in output:
            errors.append("sdk_version_mismatch")
    
    if "unresolved reference" in output.lower():
        errors.append("compile_error")
    
    if "cannot resolve symbol" in output.lower():
        errors.append("symbol_not_found")
    
    return errors

def auto_fix(errors):
    """尝试自动修复"""
    log(f"检测到问题：{errors}")
    
    workspace = Path(__file__).parent.parent
    
    for error in errors:
        if error == "compile_error" or error == "symbol_not_found":
            log("尝试修复编译错误...")
            fix_compile_errors(workspace)
        
        elif error == "resource_error":
            log("尝试修复资源错误...")
            fix_resource_errors(workspace)
        
        elif error == "auth_error":
            log("认证错误，可能需要检查 Git 配置")
            return False
    
    # 尝试重新构建
    log("执行修复后重新构建...")
    result = attempt_build(workspace)
    return result

def fix_compile_errors(workspace):
    """修复编译错误"""
    build_gradle = workspace / "app" / "build.gradle.kts"
    if build_gradle.exists():
        content = build_gradle.read_text()
        
        dependencies = [
            'implementation("androidx.core:core-ktx:1.12.0")',
            'implementation("androidx.appcompat:appcompat:1.6.1")',
            'implementation("com.google.android.material:material:1.11.0")',
            'implementation("androidx.recyclerview:recyclerview:1.3.2")',
            'implementation("androidx.cardview:cardview:1.0.0")',
        ]
        
        for dep in dependencies:
            if dep not in content:
                log(f"添加缺失的依赖：{dep}")
                content = content.replace(
                    "dependencies {",
                    f"dependencies {{\n    {dep}"
                )
        
        build_gradle.write_text(content)

def fix_resource_errors(workspace):
    """修复资源错误"""
    manifest = workspace / "app" / "src" / "main" / "AndroidManifest.xml"
    if manifest.exists():
        content = manifest.read_text()
        if "android:theme" not in content:
            log("添加主题配置")
            content = content.replace(
                'android:name=".MainActivity"',
                'android:name=".MainActivity"\n            android:theme="@style/Theme.AppCompat.Light.DarkActionBar"'
            )
            manifest.write_text(content)

def attempt_build(workspace):
    """尝试构建"""
    try:
        gradlew = workspace / "gradlew"
        if not gradlew.exists():
            log("gradlew 不存在")
            return False
        
        os.chmod(gradlew, 0o755)
        
        result = subprocess.run(
            [str(gradlew), "clean", "assembleDebug"],
            cwd=workspace,
            capture_output=True,
            text=True,
            timeout=300
        )
        
        if result.returncode == 0:
            log("构建成功")
            return True
        else:
            log(f"构建失败：{result.stderr[:500]}")
            return False
    except subprocess.TimeoutExpired:
        log("构建超时")
        return False
    except Exception as e:
        log(f"构建异常：{e}")
        return False

def commit_and_push(message):
    """提交并推送修复"""
    try:
        subprocess.run(["git", "add", "-A"], check=True, capture_output=True)
        result = subprocess.run(
            ["git", "diff", "--cached", "--quiet"],
            capture_output=True
        )
        if result.returncode != 0:
            subprocess.run(
                ["git", "commit", "-m", message],
                check=True,
                capture_output=True
            )
            subprocess.run(["git", "push"], check=True, capture_output=True)
            log("已提交并推送修复")
            return True
        else:
            log("没有需要提交的更改")
            return True
    except subprocess.CalledProcessError as e:
        log(f"Git 操作失败：{e}")
        return False

def stop_monitor():
    """停止监控任务"""
    log("构建成功，停止监控任务")
    if STATE_FILE.exists():
        STATE_FILE.unlink()
    log("状态文件已删除，监控任务已停止")

def start_monitor():
    """启动监控任务"""
    STATE_FILE.touch()
    log("监控任务已启动，状态文件已创建")

def is_monitoring_active():
    """检查监控是否处于活动状态"""
    return STATE_FILE.exists()

def main():
    log("=" * 50)
    log("开始检查构建状态")
    
    if not is_monitoring_active():
        log("监控任务未处于活动状态，跳过本次检查")
        return
    
    status = check_workflow_status()
    
    if status == "success":
        log("构建成功！暂停监控任务")
        stop_monitor()
    
    elif status == "failure" or status == "cancelled" or status == "timed_out":
        log(f"构建失败 (conclusion={status})，尝试自动修复")
        workspace = Path(__file__).parent.parent
        
        build_result = attempt_build(workspace)
        if not build_result:
            log("本地构建也失败了，尝试修复...")
            if auto_fix(["compile_error"]):
                commit_and_push("fix: 自动修复构建问题")
    
    elif status == "in_progress":
        log("构建正在进行中，等待下次检查")
    
    elif status == "no_runs":
        log("没有构建记录，触发一次构建")
        trigger_workflow()
    
    else:
        log(f"未知状态：{status}")

def trigger_workflow():
    """触发工作流运行"""
    token = get_github_token()
    url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/actions/workflows/{WORKFLOW_FILE}/dispatches"
    
    req = urllib.request.Request(url, method="POST")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("Content-Type", "application/json")
    
    data = json.dumps({"ref": "master"}).encode()
    
    try:
        with urllib.request.urlopen(req, data=data, timeout=30) as response:
            log("已触发新的构建")
    except urllib.error.HTTPError as e:
        log(f"触发构建失败：{e.code} {e.reason}")

if __name__ == "__main__":
    main()
