import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SearchCrawler implements Runnable{

	/*disallowListCache缓存robot.txt规定的不能搜索的URL*/
	private HashMap<String,ArrayList<String>> disallowListCache=new HashMap<String,ArrayList<String>>();
	ArrayList<String> errorList=new ArrayList<String>();//错误信息
	ArrayList result=new ArrayList<String>();//搜索到的结果
	String startUrl;//开始搜索的起点
	int maxUrl;//最大处理的url数
	String searchString;//要搜索的字符串
	boolean caseSensitive=false;//是否区分大小写
	boolean limitHost=false;//是否在限制的主机内容搜索
	
	public SearchCrawler(String startUrl,int maxUrl,String searchString){
		this.startUrl=startUrl;
		this.maxUrl=maxUrl;
		this.searchString=searchString;
	}
	
	public ArrayList<String> getResult(){
		return result;
	}
	
	/*启动搜索线程*/
	public void run() {
		crawl(startUrl,maxUrl,searchString,limitHost,caseSensitive);
		
	}
	//执行实际的搜索操作
	public ArrayList<String> crawl(String startUrl,int maxUrls,String searchString,boolean limithost,boolean caseSensitive){
		System.out.println("searchString="+searchString);
		HashSet<String> crawledList=new HashSet<String>();
		LinkedHashSet<String> toCrawlList=new LinkedHashSet<String>();
		
		if(maxUrls<1){//判断用户输入要处理的最大URL数
			errorList.add("InvalidMax URLs value");
			System.out.println("InvalidMax URLs value");
		}
		
		if(searchString.length()<1){//判断用户输入的搜索字符串是否为空
			errorList.add("Missing SearchString");
			System.out.println("Missing SearchString");
		}
		if(errorList.size()>0){
			System.out.println("error!");
			return errorList;
		}
		//从开始URL中移出www
		startUrl=removeWwwFromUrl(startUrl);
		toCrawlList.add(startUrl);
		while (toCrawlList.size()>0){//至少了有了一个srtarturl
			if(maxUrls!=-1){//         前面已经判断过非负
				if(crawledList.size()==maxUrls){//搜索出来的url达到用户输入的要处理的最大url数时，停止
					break;
				}
			}
			String url=toCrawlList.iterator().next();//获取list的下一个元素，实质上也是最后一个元素
			toCrawlList.remove(url);//从list中移除url
			URL verifiedUrl=verifyUrl(url);//转换String类型的url为URL对象
			if(!isRobotAllowed(verifiedUrl)){
				continue;
			}
			
			//增加已处理的URL到crawledList
			crawledList.add(url);
			String pageContents=downloadPage(verifiedUrl);//下载页面中的内容，返回字符串
			if(pageContents!=null&&pageContents.length()>0){
				//从页面中获取有效链接
				ArrayList<String> links=retrieveLinks(verifiedUrl,pageContents,crawledList,limitHost);
				toCrawlList.addAll(links);
				if(searchStringMatches(pageContents,searchString,caseSensitive)){
					result.add(url);
					System.out.println(url);
				}
			}
			
		}
		return result;
	}
	/**
	 * 从URL中去掉www
	 * @param url
	 * @return
	 */
	private String removeWwwFromUrl(String url){
		int index=url.indexOf("://www.");
		if(index!=-1){
			return url.substring(0,index+3)+url.substring(index+7);
		}
		return url;
	}
	
	
	/**
	 * 检测URL格式
	 * @param url
	 * @return
	 */
	private URL verifyUrl(String url){
		//只处理HTTP URLs
		if(!url.toLowerCase().startsWith("http://"))
			return null;
		
		URL verifiedUrl=null;
		try{
			verifiedUrl=new URL(url);//转换为URL对象
		}catch(Exception e){
				return null;
		}
		return verifiedUrl;
	}

	/**
	 * 检测robot是够允许访问给出的URL
	 * 
	 */
	private boolean isRobotAllowed(URL urlToCheck){
		String host=urlToCheck.getHost().toLowerCase();//获取给出URL的主机
		/*System.out.println("主机是："+host);*/
		//获取主机不允许搜索的URL缓存
		ArrayList<String> disallowList=disallowListCache.get(host);
		//如果还没有缓存，下载并缓存
		if(disallowList==null){
			disallowList=new ArrayList<String>();
			try{
				URL robotsFileUrl=new URL("http://"+host+"/robots.txt");
				BufferedReader reader=new BufferedReader(new InputStreamReader(robotsFileUrl.openStream()));
				//读取robot文件，创建不允许访问的路径列表
				String line;
				while((line=reader.readLine())!=null){
					if(line.indexOf("disallow:")==0){//是否包含"Disallow"
						String disallowPath=line.substring("Disallow:".length());//获取不允许访问路径，即Disallow:后面的字符串
						//检查是否有注释
						int commentIndex=disallowPath.indexOf("#");
						if(commentIndex!=-1){//即找到了
							disallowPath=disallowPath.substring(0,commentIndex);//去掉注释
						}
						disallowPath=disallowPath.trim();//去除空白
						disallowList.add(disallowPath);
						
					}
				}
				disallowListCache.put(host, disallowList);//缓存此主机不允许访问的路径
				
			}catch(Exception e){
				return true;//web站点下根目录下没有rotbots.txt文件，返回真
			}
		}
		
		String file=urlToCheck.getFile();//与getPath（）类似，返回路径名
		/*System.out.println("文件getFile()="+file);*/
		for(int i=0;i<disallowList.size();i++){//逐个将每个url做判断，是否为禁止访问的url，如果是，返回true
			String disallow=disallowList.get(i);
			if(file.startsWith(disallow)){
				return false;
			}
		}
			return true;
	}
	
	/**
	 * downloadPage
	 */
	private String downloadPage(URL pageUrl){
		try{
			//打开URL链接进行读取
			BufferedReader reader=new BufferedReader(new InputStreamReader(pageUrl.openStream()));
			//将page读进buffer
			String line;
			StringBuffer pageBuffer=new StringBuffer();
			while((line=reader.readLine())!=null){
				pageBuffer.append(line);
			}
			return pageBuffer.toString();
			
		}catch(Exception e){
			
		}
		return null;
	}


	/**
	 * j解析页面并找出链接
	 * @param pageUrl
	 * @param pageContents
	 * @param crawledList
	 * @param limitHost
	 * @return
	 */
	 private ArrayList<String> retrieveLinks(URL pageUrl,String pageContents,HashSet crawledList,boolean limitHost){
		 //用正则表达式编译链接的匹配模式
		 Pattern p=Pattern.compile("<a\\s+href\\s*=\\s*\"?(.*?)[\"|>]",Pattern.CASE_INSENSITIVE);//CASE_INSENSITIVE:使不区分大小写的匹配。
		 Matcher m=p.matcher(pageContents);
		 
		 ArrayList<String> linkList=new ArrayList<String>();
		 while(m.find()){//find():尝试查找与该模式匹配的输入序列的下一个子序列。
			 String link=m.group(1).trim();//group(int group):返回在以前匹配操作期间由给定组捕获的输入子序列。
			 if(link.length()<1){
				 continue;
			 }
			 //跳过链到本页面内的链接
			 if(link.charAt(0)=='#'){
				 continue;
			 }
			 
			 if(link.indexOf("mailto:")!=-1){//邮件地址
				 continue;
			 }
			 if(link.toLowerCase().indexOf("javascript")!=-1){
				 continue;
			 }
			 
			 if(link.indexOf("://")==-1){
				 if(link.charAt(0)=='/'){//处理绝对地址
					 link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+link;
				 }else{
					 String file=pageUrl.getFile();
					 if(file.indexOf('/')==-1){//处理相对地址
						 link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+"/"+link;
					 }else{
						 String path=file.substring(0,file.lastIndexOf('/')+1);
						 link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+path+link;
					 }
				 }
			 }
			 int index=link.indexOf('#');
			 if(index!=-1){
				 link=link.substring(0,index);
			 }
			 link=removeWwwFromUrl(link);
			 
			 URL verifiedLink=verifyUrl(link);
			 if(verifiedLink==null){
				 continue;
			 }
			 //如果限定主机，排除那些不合条件的URL
			 if(limitHost&&!pageUrl.getHost().equals(verifiedLink.getHost().toLowerCase())){
				 continue;
			 }
			 
			 //跳过那些已经处理的链接
			 if(crawledList.contains(link)){
				 continue;
			 }
			 linkList.add(link);
			 
		 }
		 return linkList;
	 }
	
	 
	 //搜索下载web页面的内容，判断在该页面内有没有指定的搜索字符串
	 private boolean searchStringMatches(String pageContents,String searchString,boolean caseSensitive){
		 String searchContents=pageContents;
		 if(!caseSensitive){//如果不区分大小写
			 searchContents=pageContents.toLowerCase();
		 }
		 Pattern p=Pattern.compile("[\\s]+");
		 String[] terms=p.split(searchString);
		 for(int i=0;i<terms.length;i++){
			 if(caseSensitive){
				 if(searchContents.indexOf(terms[i])== -1){
					 return false;
				 }
			 }else{
				 if(searchContents.indexOf(terms[i].toLowerCase())== -1){
					 return false;
				 }
			 }
		 }
		 return true;
	 }
	 
	 public static void main(String[] args){
		 SearchCrawler crawler=new SearchCrawler("http://www.sina.com.cn/", 50, "女");
		 Thread search=new Thread(crawler);
		 System.out.println("start searching ...");
		 System.out.println("result:");
		 search.start();
	 }

}