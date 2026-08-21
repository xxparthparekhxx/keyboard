from huggingface_hub import hf_hub_download
R = "futo-org/swipe.futo.org"
for f in ["swipe-5/layouts/qwerty.json", "dev.jsonl", "test.jsonl",
          "swipe-2/swipe2.jsonl", "swipe-3/swipe3.jsonl",
          "swipe-4/swipe4.jsonl", "swipe-5/swipe5.jsonl", "train.jsonl"]:
    p = hf_hub_download(R, f, repo_type="dataset", local_dir="data/hf")
    print("ok", f, flush=True)
print("ALL DONE")
