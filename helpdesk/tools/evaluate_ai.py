"""Reproducible local NLP baseline evaluation on an isolated temporary DB."""
from pathlib import Path
import json
import os
import sys
import tempfile
import time

sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from app import ai, db

root=Path(__file__).resolve().parent.parent
dataset=json.loads((root/'data_examples'/'evaluation.json').read_text(encoding='utf-8'))
os.environ.pop('HELPDESK_OLLAMA_MODEL',None)
started=time.perf_counter()
predictions=[{'text':case['text'],'expected':case['label'],'predicted':ai.classify(case['text'])[0]} for case in dataset['classification']]
metrics={}
for label in ai.TRAINING:
    tp=sum(p['expected']==label and p['predicted']==label for p in predictions)
    fp=sum(p['expected']!=label and p['predicted']==label for p in predictions)
    fn=sum(p['expected']==label and p['predicted']!=label for p in predictions)
    precision=tp/(tp+fp) if tp+fp else 0
    recall=tp/(tp+fn) if tp+fn else 0
    metrics[label]={'precision':precision,'recall':recall,'f1':2*precision*recall/(precision+recall) if precision+recall else 0}
with tempfile.TemporaryDirectory(prefix='helpdesk-eval-') as directory:
    path=Path(directory)/'evaluation.sqlite3'
    db.initialize(path)
    with db.transaction(path) as conn:
        articles=[dict(a) for a in conn.execute("SELECT * FROM kb_articles WHERE status='PUBLISHED'")]
    retrieved=[{'text':case['text'],'expected':case['article_id'],'actual':ranked[0][1]['id'] if (ranked:=ai.retrieve(case['text'],articles)) else None} for case in dataset['retrieval']]
    fallback=[{'text':text,'transferred':ai.suggest(text,articles)['answer'] is None} for text in dataset['fallback']]
    latencies=[]
    grounded=[]
    for case in dataset['retrieval']:
        one_started=time.perf_counter()
        answer=ai.suggest(case['text'],articles)
        latencies.append((time.perf_counter()-one_started)*1000)
        grounded.append(any(answer['answer']==article['body'] and any(s['id']==article['id'] for s in answer['sources']) for article in articles))
result={
    'mode':'LOCAL_EXTRACTIVE',
    'limitations':dataset['description'],
    'classification_count':len(predictions),
    'accuracy':sum(p['expected']==p['predicted'] for p in predictions)/len(predictions),
    'macro_f1':sum(m['f1'] for m in metrics.values())/len(metrics),
    'per_category':metrics,
    'retrieval_top1':sum(r['expected']==r['actual'] for r in retrieved)/len(retrieved),
    'fallback_success':sum(f['transferred'] for f in fallback)/len(fallback),
    'extractive_source_match':sum(grounded)/len(grounded),
    'mean_local_suggestion_ms':round(sum(latencies)/len(latencies),3),
    'external_calls':0,
    'classification':predictions,'retrieval':retrieved,'fallback':fallback,
    'elapsed_ms_including_database_setup':round((time.perf_counter()-started)*1000,1),
    'external_model_tested':False,
}
output=root/'data'/'evaluation-result.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in ('classification','retrieval','fallback','limitations','per_category')},ensure_ascii=True,indent=2))
print(output)
