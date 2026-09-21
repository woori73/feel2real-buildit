#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Structure the 감면율 board collected articles into NewStart AI case dataset.
Enhanced version with content parsing.
"""

import json
import os
import re
from datetime import datetime
from typing import Dict, List, Any, Optional

ARTICLES_DIR = r"C:\feel2real-buildit\data\newstart-cafe\gamian\articles"
OUTPUT_DIR = r"C:\feel2real-buildit\data\newstart-cafe\gamian\structured"

# Comment role classification
COMMENT_ROLES = {
    "AUTHOR_RESULT": "작성자가 후속 댓글로 실제 확정 결과를 알려준 경우",
    "AUTHOR_FOLLOWUP": "작성자의 추가 질문/후속 대응",
    "COMMUNITY_EXPERIENCE": "다른 회원의 실제 경험/체결 후기",
    "COMMUNITY_PREDICTION": "다른 회원의 추측/예측",
    "COMMUNITY_OPINION": "다른 회원의 의견/조언",
    "QUESTION": "질문",
    "OTHER": "기타"
}

MANAGER_NICKNAME = "지피지기자산"

def classify_comment(comment: Dict, author_nickname: str, manager_nickname: str = MANAGER_NICKNAME) -> str:
    """Classify comment role based on author and content."""
    author = comment.get("author", {}).get("nickname", "")
    content = comment.get("content", "")
    
    if author == author_nickname:
        # Check if it's a result report
        result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "74나왔", "75나왔", "프로 나왔"]
        if any(kw in content for kw in result_keywords):
            return "AUTHOR_RESULT"
        return "AUTHOR_FOLLOWUP"
    
    if author == manager_nickname:
        return "COMMUNITY_PREDICTION"
    
    # Other members
    experience_keywords = ["받았", "나왔", "체결", "확정", "제출", "진행", "승인", "했음", "했습니다", "임시안"]
    if any(kw in content for kw in experience_keywords) and len(content) > 30:
        return "COMMUNITY_EXPERIENCE"
    
    prediction_keywords = ["예상", "예측", "생각", "같네요", "될까요", "될듯", "가능", "추정"]
    if any(kw in content for kw in prediction_keywords):
        return "COMMUNITY_PREDICTION"
    
    if "?" in content or "여쭙" in content or "문의" in content or "궁금" in content:
        return "QUESTION"
    
    return "COMMUNITY_OPINION"

def extract_number(text: str, pattern: str) -> Optional[float]:
    """Extract number from text using regex."""
    match = re.search(pattern, text)
    if match:
        try:
            return float(match.group(1).replace(",", ""))
        except:
            return None
    return None

def parse_amount_korean(text: str) -> Optional[int]:
    """Parse Korean amount expressions like '1억 5천만원', '3억2천', '5000만' etc."""
    if not text:
        return None
    
    total = 0
    
    # 억 단위
    eok_matches = re.findall(r'(\d+(?:\.\d+)?)\s*억', text)
    for m in eok_matches:
        total += float(m) * 100000000
    
    # 천만 단위
    cheonman_matches = re.findall(r'(\d+(?:\.\d+)?)\s*천만', text)
    for m in cheonman_matches:
        total += float(m) * 10000000
    
    # 만 단위
    man_matches = re.findall(r'(\d+(?:\.\d+)?)\s*만', text)
    for m in man_matches:
        total += float(m) * 10000
    
    # 원 단위 (숫자만 있는 경우)
    if total == 0:
        won_matches = re.findall(r'(\d{1,3}(?:,\d{3})+)\s*원', text)
        for m in won_matches:
            total += float(m.replace(",", ""))
    
    return int(total) if total > 0 else None

def extract_debt_info(content: str) -> Dict:
    """Extract debt information from content."""
    result = {
        "total": None,
        "breakdown": [],
        "secured": None,
        "unsecured": None,
        "acquired_by_fund": None
    }
    
    # Total debt patterns
    total_patterns = [
        r'총\s*채무[:\s]*([\d억천만원,\s]+)',
        r'총채무[:\s]*([\d억천만원,\s]+)',
        r'총\s*부채[:\s]*([\d억천만원,\s]+)',
        r'합계[:\s]*([\d억천만원,\s]+)',
        r'총\s*합계[:\s]*([\d억천만원,\s]+)',
    ]
    
    for pattern in total_patterns:
        match = re.search(pattern, content)
        if match:
            amount = parse_amount_korean(match.group(1))
            if amount:
                result["total"] = amount
                break
    
    # If no total found, try to sum breakdown
    if result["total"] is None:
        # Look for individual debt items
        debt_items = re.findall(r'([가-힣\w\s]+)\s*[:]\s*([\d억천만원,\s]+)', content)
        for name, amount_str in debt_items:
            amount = parse_amount_korean(amount_str)
            if amount and amount > 100000:  # Only significant amounts
                result["breakdown"].append({
                    "creditor": name.strip(),
                    "amount": amount,
                    "type": "unknown"
                })
    
    return result

def extract_asset_info(content: str) -> Dict:
    """Extract asset information from content."""
    result = {
        "property": None,
        "vehicle": None,
        "financial": None,
        "other": None
    }
    
    # Property
    prop_patterns = [
        r'(아파트|주택|빌라|집|부동산|토지)[^\d]*([\d억천만원,\s]+)',
        r'(kb시세|공시지가|시세)[^\d]*([\d억천만원,\s]+)',
        r'(자가|자가소유|공동명의)[^\d]*([\d억천만원,\s]+)',
    ]
    
    for pattern in prop_patterns:
        matches = re.findall(pattern, content)
        for match in matches:
            amount = parse_amount_korean(match[1]) if len(match) > 1 else parse_amount_korean(match[0])
            if amount and amount > 1000000:
                result["property"] = amount
                break
    
    # Vehicle
    vehicle_patterns = [
        r'(차량|차|승용차|자동차)[^\d]*([\d억천만원,\s]+)',
        r'(시세|중고가)[^\d]*([\d억천만원,\s]+).*차',
    ]
    
    for pattern in vehicle_patterns:
        matches = re.findall(pattern, content)
        for match in matches:
            amount = parse_amount_korean(match[1]) if len(match) > 1 else parse_amount_korean(match[0])
            if amount and amount > 100000:
                result["vehicle"] = amount
                break
    
    # Financial assets
    fin_patterns = [
        r'(보증금|예금|적금|통장|환급금|보험|주식|코인|금융자산)[^\d]*([\d억천만원,\s]+)',
        r'(임대차보증금|전세보증금|월세보증금)[^\d]*([\d억천만원,\s]+)',
    ]
    
    for pattern in fin_patterns:
        matches = re.findall(pattern, content)
        for match in matches:
            amount = parse_amount_korean(match[1]) if len(match) > 1 else parse_amount_korean(match[0])
            if amount and amount > 100000:
                result["financial"] = amount
                break
    
    return result

def extract_income_info(content: str) -> Dict:
    """Extract income information from content."""
    result = {
        "income_2025": None,
        "income_current": None,
        "income_breakdown": {}
    }
    
    # 2025 income
    patterns = [
        r'25년\s*소득[^\d]*([\d억천만원,\s]+)',
        r'2025년\s*소득[^\d]*([\d억천만원,\s]+)',
        r'소득금액[^\d]*([\d억천만원,\s]+)',
        r'사업소득[^\d]*([\d억천만원,\s]+)',
        r'근로소득[^\d]*([\d억천만원,\s]+)',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, content)
        if match:
            amount = parse_amount_korean(match.group(1))
            if amount:
                result["income_2025"] = amount
                break
    
    # Monthly income
    monthly_patterns = [
        r'월\s*(\d+(?:,\d{3})*)\s*만',
        r'월급\s*([\d억천만원,\s]+)',
        r'세전\s*([\d억천만원,\s]+)',
        r'세후\s*([\d억천만원,\s]+)',
    ]
    
    for pattern in monthly_patterns:
        match = re.search(pattern, content)
        if match:
            amount = parse_amount_korean(match.group(1))
            if amount:
                result["income_current"] = amount
                break
    
    return result

def extract_personal_info(content: str) -> Dict:
    """Extract personal/household information."""
    result = {
        "age": None,
        "household_type": None,
        "dependents": None,
        "employment_status": None,
        "business_status": None,
        "application_type": None,
        "application_date": None,
        "region": None
    }
    
    # Age
    age_match = re.search(r'(나이|연령|생년월일|[0-9]{2}년생|[0-9]{4}년\s*생)[^\d]*([0-9]{1,2}세?)', content)
    if not age_match:
        age_match = re.search(r'([0-9]{2})세', content)
    if age_match:
        try:
            age_str = age_match.group(2) if age_match.lastindex == 2 else age_match.group(1)
            age_num = re.search(r'(\d+)', age_str)
            if age_num:
                result["age"] = int(age_num.group(1))
        except:
            pass
    
    # Household
    household_match = re.search(r'(\d+)인가구|(\d+)인\s*가구|가족\s*(\d+)명', content)
    if household_match:
        for g in household_match.groups():
            if g:
                result["household_type"] = f"{g}인가구"
                break
    
    # Dependents
    dep_match = re.search(r'(미성년\s*자녀\s*(\d+)명|자녀\s*(\d+)명)', content)
    if dep_match:
        for g in dep_match.groups():
            if g and g.isdigit():
                result["dependents"] = int(g)
                break
    
    # Employment/Business status
    if "폐업" in content:
        result["business_status"] = "CLOSED"
    elif "사업자" in content and "유지" in content:
        result["business_status"] = "OPERATING"
    elif "직장" in content and ("재직" in content or "다니" in content):
        result["employment_status"] = "EMPLOYED"
    elif "무직" in content:
        result["employment_status"] = "UNEMPLOYED"
    
    # Application type
    if "부실차주" in content and "우려" in content:
        result["application_type"] = "INSOLVENCY_RISK"
    elif "부실차주" in content:
        result["application_type"] = "INSOLVENCY"
    
    # Region
    region_match = re.search(r'(인천|광주|부산|청주|경북|경상도|전남|완도|안양|서울|수도권|지방)[시도군구]?', content)
    if region_match:
        result["region"] = region_match.group(1)
    
    return result

def extract_reduction_info(content: str) -> Dict:
    """Extract reduction rate and repayment information."""
    result = {
        "predicted_reduction_rate": None,
        "predicted_repayment_amount": None,
        "final_reduction_rate": None,
        "final_repayment_amount": None,
        "repayment_period_months": None,
        "grace_period_months": None,
        "monthly_payment": None,
        "temp_reduction_rate": None
    }
    
    # Predicted reduction rate
    pred_patterns = [
        r'예상\s*감면율[:\s]*([0-9~%-]+)',
        r'감면율[:\s]*([0-9~%-]+)\s*예상',
        r'감면율\s*([0-9]+)%',
        r'([0-9]+)~([0-9]+)%',
        r'([0-9]+)%\s*감면',
    ]
    
    rates = []
    for pattern in pred_patterns:
        matches = re.findall(pattern, content)
        for match in matches:
            if isinstance(match, tuple):
                for m in match:
                    try:
                        rates.append(int(m))
                    except:
                        pass
            else:
                try:
                    rates.append(int(match))
                except:
                    pass
    
    if rates:
        # Take the most specific range
        if len(rates) >= 2:
            result["predicted_reduction_rate"] = f"{min(rates)}-{max(rates)}%"
        else:
            result["predicted_reduction_rate"] = f"{rates[0]}%"
    
    # Final/temp reduction rate
    final_patterns = [
        r'임시안\s*([0-9]+)%',
        r'확정\s*감면율\s*([0-9]+)%',
        r'최종\s*감면율\s*([0-9]+)%',
    ]
    
    for pattern in final_patterns:
        match = re.search(pattern, content)
        if match:
            try:
                result["temp_reduction_rate"] = int(match.group(1))
            except:
                pass
            break
    
    # Repayment amount
    repay_patterns = [
        r'최종\s*상환[액금][:\s]*([\d억천만원,\s]+)',
        r'상환[액금][:\s]*([\d억천만원,\s]+)',
        r'변제[액금][:\s]*([\d억천만원,\s]+)',
    ]
    
    for pattern in repay_patterns:
        match = re.search(pattern, content)
        if match:
            amount = parse_amount_korean(match.group(1))
            if amount:
                result["predicted_repayment_amount"] = amount
                break
    
    # Period
    period_match = re.search(r'(\d+)년\s*분할|(\d+)개월|(\d+)월\s*분할|거치\s*(\d+)', content)
    if period_match:
        for g in period_match.groups():
            if g:
                try:
                    result["repayment_period_months"] = int(g) * (12 if '년' in period_match.group(0) else 1)
                except:
                    pass
                break
    
    # Grace period
    grace_match = re.search(r'거치\s*(\d+)', content)
    if grace_match:
        try:
            result["grace_period_months"] = int(grace_match.group(1))
        except:
            pass
    
    # Monthly payment
    monthly_match = re.search(r'월\s*약\s*([\d,]+)\s*만?원', content)
    if monthly_match:
        try:
            result["monthly_payment"] = int(monthly_match.group(1).replace(",", "")) * 10000
        except:
            pass
    
    return result

def extract_special_circumstances(content: str) -> List[str]:
    """Extract special circumstances mentioned."""
    circumstances = []
    keywords = [
        ("장애", "DISABILITY"),
        ("암", "CANCER"),
        ("주거급여", "HOUSING_BENEFIT"),
        ("희망리턴", "HOPE_RETURN_PACKAGE"),
        ("국민취업", "KUKMIN_EMPLOYMENT"),
        ("거치", "GRACE_PERIOD"),
        ("공매", "PUBLIC_AUCTION"),
        ("사해행위", "FRAUDULENT_TRANSFER"),
        ("공동명의", "JOINT_OWNERSHIP"),
        ("보증", "GUARANTEE"),
        ("신보", "CREDIT_GUARANTEE"),
        ("소진공", "SME_FUND"),
        ("기술보증", "TECH_GUARANTEE"),
        ("지역공제", "REGIONAL_DEDUCTION"),
    ]
    
    for keyword, code in keywords:
        if keyword in content:
            circumstances.append(code)
    
    return circumstances

def extract_manager_prediction(comments: List[Dict]) -> Dict:
    """Extract manager's prediction from comments."""
    for comment in comments:
        if comment.get("author", {}).get("nickname") == MANAGER_NICKNAME:
            content = comment.get("content", "")
            pred = extract_reduction_info(content)
            return {
                "content": content,
                "predicted_rate": pred.get("predicted_reduction_rate"),
                "predicted_repayment": pred.get("predicted_repayment_amount"),
                "period_months": pred.get("repayment_period_months"),
                "monthly_payment": pred.get("monthly_payment")
            }
    return {}

def check_reply_for_result(reply: Dict, author: str) -> Optional[str]:
    """Recursively check a reply and its nested replies for result keywords."""
    result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔", "나왔어유"]
    
    if reply.get("author", {}).get("nickname") == author:
        content = reply.get("content", "")
        if any(kw in content for kw in result_keywords):
            return content
    
    # Check nested replies
    for nested_reply in reply.get("replies", []):
        result = check_reply_for_result(nested_reply, author)
        if result:
            return result
    return None


def extract_author_followup_result(comments: List[Dict], author: str) -> Optional[str]:
    """Extract author's followup with actual result."""
    result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔", "나왔어유"]
    
    # Check top-level comments
    for comment in comments:
        if comment.get("author", {}).get("nickname") == author:
            content = comment.get("content", "")
            if any(kw in content for kw in result_keywords):
                return content
        
        # Check all nested replies recursively
        for reply in comment.get("replies", []):
            result = check_reply_for_result(reply, author)
            if result:
                return result
    return None


def extract_author_followup_from_content(content: str, author: str) -> Optional[str]:
    """Extract author followup result from raw content text as fallback."""
    # Look for author's replies in the raw content
    result_keywords = ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔", "나왔어유"]
    
    # Split content by lines and look for author's responses
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if author in line:
            # Check this line and next few lines for result keywords
            check_text = '\n'.join(lines[i:min(i+5, len(lines))])
            if any(kw in check_text for kw in ["나왔습니다", "나왔어", "확정", "체결", "받았습니다", "결과", "최종", "임시안", "프로 나왔", "나왔어유", "프로나왔", "나왔어유"]):
                return check_text[:500]
    return None


def extract_actual_rate_from_followup(followup_text: str) -> Optional[str]:
    """Extract actual reduction rate from author followup text."""
    if not followup_text:
        return None
    
    # Patterns for actual rates mentioned in followup
    patterns = [
        r'임시안\s*(?:에|에서)?\s*(\d+)\s*프로',
        r'임시안\s*(\d+)프로',
        r'임시안\s*(\d+)\s*%',
        r'(\d+)\s*프로\s*나왔',
        r'(\d+)%\s*나왔',
        r'확정\s*감면율\s*(\d+)',
        r'최종\s*감면율\s*(\d+)',
        r'감면율\s*(\d+)\s*확정',
        r'임시안\s*(?:오늘\s*)?(\d+)\s*나왔어유',
        r'임시안\s*(\d+)\s*나왔',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, followup_text)
        if match:
            try:
                return f"{match.group(1)}%"
            except:
                pass
    return None

def extract_structured_case(article: Dict) -> Dict:
    """Extract structured case from article."""
    article_id = article.get("articleId", "")
    title = article.get("title", "")
    author = article.get("author", {}).get("nickname", "")
    created_at = article.get("createdAt", "")
    view_count = article.get("viewCount", 0)
    content = article.get("content", "")
    comments = article.get("comments", [])
    comment_count = article.get("commentCount", 0)
    reply_count = article.get("replyCount", 0)
    source_url = article.get("sourceUrl", "")
    collected_at = article.get("collectedAt", "")
    has_image_only = article.get("hasImageOnlyData", False)
    
    # Classify comments
    classified_comments = []
    for comment in comments:
        role = classify_comment(comment, author)
        classified_comments.append({
            "comment_id": comment.get("commentId", ""),
            "author": comment.get("author", {}).get("nickname", ""),
            "author_grade": comment.get("author", {}).get("grade", ""),
            "is_author": comment.get("author", {}).get("isAuthor", False),
            "created_at": comment.get("createdAt", ""),
            "content": comment.get("content", ""),
            "parent_id": comment.get("parentId"),
            "role": role,
            "replies": [
                {
                    "comment_id": r.get("commentId", ""),
                    "author": r.get("author", {}).get("nickname", ""),
                    "author_grade": r.get("author", {}).get("grade", ""),
                    "is_author": r.get("author", {}).get("isAuthor", False),
                    "created_at": r.get("createdAt", ""),
                    "content": r.get("content", ""),
                    "parent_id": r.get("parentId"),
                    "role": classify_comment(r, author)
                }
                for r in comment.get("replies", [])
            ]
        })
    
    # Determine case type
    case_type = "PREDICTION_REQUEST"
    if "임시안" in title or "임시안" in content[:200]:
        case_type = "TEMP_RESULT"
    if "확정" in title or "체결" in title or "받았" in title:
        case_type = "FINAL_RESULT"
    if "자문" in title or "받은" in title or "주의" in title:
        case_type = "ADVICE_REQUEST"
    
    # Extract all structured info
    debt_info = extract_debt_info(content)
    asset_info = extract_asset_info(content)
    income_info = extract_income_info(content)
    personal_info = extract_personal_info(content)
    reduction_info = extract_reduction_info(content)
    special_circumstances = extract_special_circumstances(content)
    manager_pred = extract_manager_prediction(comments)
    author_followup = extract_author_followup_result(comments, author)
    if not author_followup:
        author_followup = extract_author_followup_from_content(content, author)
    
    # Extract actual rate from followup
    actual_rate_from_followup = extract_actual_rate_from_followup(author_followup) if author_followup else None
    
    # Use followup actual rate as final_reduction_rate if not already in content
    final_reduction_rate = reduction_info.get("final_reduction_rate")
    if not final_reduction_rate and actual_rate_from_followup:
        final_reduction_rate = actual_rate_from_followup
    
    # Prediction vs actual
    pred_vs_actual = None
    if manager_pred.get("predicted_rate") and author_followup:
        pred_vs_actual = {
            "predicted": manager_pred.get("predicted_rate"),
            "actual_report": author_followup[:200]
        }
    
    # Key insights
    insights = []
    if personal_info.get("age") and personal_info["age"] >= 70:
        insights.append("SENIOR_70_PLUS_HIGH_REDUCTION")
    if "주거급여" in content or personal_info.get("application_type") == "INSOLVENCY_RISK":
        insights.append("HOUSING_BENEFIT_RECIPIENT")
    if "장애" in content:
        insights.append("DISABILITY_FACTOR")
    if "암" in content:
        insights.append("CANCER_FACTOR")
    if asset_info.get("property") and asset_info.get("vehicle"):
        insights.append("PROPERTY_AND_VEHICLE_OWNER")
    if income_info.get("income_2025") and income_info["income_2025"] > 50000000:
        insights.append("HIGH_INCOME_MAY_REDUCE_RATE")
    if "공동명의" in content:
        insights.append("JOINT_OWNERSHIP_PROPERTY")
    if "폐업" in content and "재직" in content:
        insights.append("CLOSED_BUT_EMPLOYED")
    
    return {
        "article_id": article_id,
        "source_url": source_url,
        "collected_at": collected_at,
        "title": title,
        "case_type": case_type,
        "author_nickname": author,
        "created_at": created_at,
        "view_count": view_count,
        "content_raw": content,
        "has_image_only_data": has_image_only,
        "comment_count": comment_count,
        "reply_count": reply_count,
        "comments": classified_comments,
        # Structured fields
        "debt_total": debt_info.get("total"),
        "debt_breakdown": debt_info.get("breakdown"),
        "debt_secured": debt_info.get("secured"),
        "debt_unsecured": debt_info.get("unsecured"),
        "debt_acquired_by_fund": debt_info.get("acquired_by_fund"),
        "asset_property": asset_info.get("property"),
        "asset_vehicle": asset_info.get("vehicle"),
        "asset_financial": asset_info.get("financial"),
        "asset_other": asset_info.get("other"),
        "household_type": personal_info.get("household_type"),
        "dependents": personal_info.get("dependents"),
        "age": personal_info.get("age"),
        "income_2025": income_info.get("income_2025"),
        "income_current": income_info.get("income_current"),
        "employment_status": personal_info.get("employment_status"),
        "business_status": personal_info.get("business_status"),
        "application_type": personal_info.get("application_type"),
        "application_date": personal_info.get("application_date"),
        "region": personal_info.get("region"),
        "predicted_reduction_rate": reduction_info.get("predicted_reduction_rate"),
        "predicted_repayment_amount": reduction_info.get("predicted_repayment_amount"),
        "final_reduction_rate": final_reduction_rate,
        "final_repayment_amount": reduction_info.get("final_repayment_amount"),
        "temp_reduction_rate": reduction_info.get("temp_reduction_rate"),
        "repayment_period_months": reduction_info.get("repayment_period_months"),
        "grace_period_months": reduction_info.get("grace_period_months"),
        "monthly_payment": reduction_info.get("monthly_payment"),
        "financial_institutions": [item["creditor"] for item in debt_info.get("breakdown", [])] if debt_info.get("breakdown") else None,
        "special_circumstances": special_circumstances,
        "author_questions": None,  # Could extract from content
        "manager_prediction": manager_pred,
        "author_followup_result": author_followup,
        "prediction_vs_actual": pred_vs_actual,
        "key_insights": insights,
        "actual_reduction_rate_from_followup": extract_actual_rate_from_followup(author_followup) if author_followup else None
    }

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Read all articles
    articles = []
    for filename in sorted(os.listdir(ARTICLES_DIR)):
        if filename.endswith(".json"):
            filepath = os.path.join(ARTICLES_DIR, filename)
            with open(filepath, "r", encoding="utf-8") as f:
                article = json.load(f)
            articles.append(article)
    
    print(f"Processing {len(articles)} articles...")
    
    # Structure all cases
    structured_cases = []
    for article in articles:
        case = extract_structured_case(article)
        structured_cases.append(case)
    
    # Save as JSONL
    jsonl_path = os.path.join(OUTPUT_DIR, "cases.jsonl")
    with open(jsonl_path, "w", encoding="utf-8") as f:
        for case in structured_cases:
            f.write(json.dumps(case, ensure_ascii=False) + "\n")
    
    # Save as JSON array
    json_path = os.path.join(OUTPUT_DIR, "cases.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(structured_cases, f, ensure_ascii=False, indent=2)
    
    # Statistics
    stats = {
        "total_articles": len(structured_cases),
        "case_types": {},
        "with_actual_reduction_rate": 0,
        "with_predicted_rate_only": 0,
        "with_confirmed_amount": 0,
        "with_author_followup": 0,
        "with_prediction_and_actual": 0,
        "unknown_insufficient": 0,
        "comment_roles": {},
        "extraction_stats": {
            "debt_total_filled": 0,
            "asset_property_filled": 0,
            "income_2025_filled": 0,
            "age_filled": 0,
            "predicted_rate_filled": 0,
            "manager_prediction_filled": 0
        }
    }
    
    for case in structured_cases:
        ct = case["case_type"]
        stats["case_types"][ct] = stats["case_types"].get(ct, 0) + 1
        
        if case["author_followup_result"]:
            stats["with_author_followup"] += 1
        if case["final_reduction_rate"] or case.get("actual_reduction_rate_from_followup"):
            stats["with_actual_reduction_rate"] += 1
        if case["predicted_reduction_rate"]:
            stats["with_predicted_rate_only"] += 1
        if case["final_repayment_amount"]:
            stats["with_confirmed_amount"] += 1
        
        if case["debt_total"]:
            stats["extraction_stats"]["debt_total_filled"] += 1
        if case["asset_property"]:
            stats["extraction_stats"]["asset_property_filled"] += 1
        if case["income_2025"]:
            stats["extraction_stats"]["income_2025_filled"] += 1
        if case["age"]:
            stats["extraction_stats"]["age_filled"] += 1
        if case["predicted_reduction_rate"]:
            stats["extraction_stats"]["predicted_rate_filled"] += 1
        if case["manager_prediction"]:
            stats["extraction_stats"]["manager_prediction_filled"] += 1
        
        for c in case["comments"]:
            stats["comment_roles"][c["role"]] = stats["comment_roles"].get(c["role"], 0) + 1
            for r in c.get("replies", []):
                stats["comment_roles"][r["role"]] = stats["comment_roles"].get(r["role"], 0) + 1
    
    # Identify cases with both prediction and actual
    for case in structured_cases:
        has_prediction = bool(case["predicted_reduction_rate"] or case["manager_prediction"])
        has_actual = bool(case["author_followup_result"] or case["final_reduction_rate"] or case.get("actual_reduction_rate_from_followup"))
        if has_prediction and has_actual:
            stats["with_prediction_and_actual"] += 1
        if not has_prediction and not has_actual:
            stats["unknown_insufficient"] += 1
    
    stats_path = os.path.join(OUTPUT_DIR, "statistics.json")
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    
    print(f"Structured cases saved to {jsonl_path} and {json_path}")
    print(f"Statistics saved to {stats_path}")
    print(f"\nStatistics:")
    print(f"  Total articles: {stats['total_articles']}")
    print(f"  Case types: {stats['case_types']}")
    print(f"  With actual reduction rate: {stats['with_actual_reduction_rate']}")
    print(f"  With predicted rate only: {stats['with_predicted_rate_only']}")
    print(f"  With confirmed amount: {stats['with_confirmed_amount']}")
    print(f"  With author followup: {stats['with_author_followup']}")
    print(f"  With prediction + actual: {stats['with_prediction_and_actual']}")
    print(f"  Unknown/insufficient: {stats['unknown_insufficient']}")
    print(f"  Comment roles: {stats['comment_roles']}")
    print(f"  Extraction stats: {stats['extraction_stats']}")

if __name__ == "__main__":
    main()