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

	/*disallowListCache����robot.txt�涨�Ĳ���������URL*/
	private HashMap<String,ArrayList<String>> disallowListCache=new HashMap<String,ArrayList<String>>();
	ArrayList<String> errorList=new ArrayList<String>();//������Ϣ
	ArrayList result=new ArrayList<String>();//�������Ľ��
	String startUrl;//��ʼ���������
	int maxUrl;//������url��
	String searchString;//Ҫ�������ַ���
	boolean caseSensitive=false;//�Ƿ����ִ�Сд
	boolean limitHost=false;//�Ƿ������Ƶ�������������
	
	public SearchCrawler(String startUrl,int maxUrl,String searchString){
		this.startUrl=startUrl;
		this.maxUrl=maxUrl;
		this.searchString=searchString;
	}
	
	public ArrayList<String> getResult(){
		return result;
	}
	
	/*���������߳�*/
	public void run() {
		crawl(startUrl,maxUrl,searchString,limitHost,caseSensitive);
		
	}
	//ִ��ʵ�ʵ���������
	public ArrayList<String> crawl(String startUrl,int maxUrls,String searchString,boolean limithost,boolean caseSensitive){
		System.out.println("searchString="+searchString);
		HashSet<String> crawledList=new HashSet<String>();
		LinkedHashSet<String> toCrawlList=new LinkedHashSet<String>();
		
		if(maxUrls<1){//�ж��û�����Ҫ��������URL��
			errorList.add("InvalidMax URLs value");
			System.out.println("InvalidMax URLs value");
		}
		
		if(searchString.length()<1){//�ж��û�����������ַ����Ƿ�Ϊ��
			errorList.add("Missing SearchString");
			System.out.println("Missing SearchString");
		}
		if(errorList.size()>0){
			System.out.println("error!");
			return errorList;
		}
		//�ӿ�ʼURL���Ƴ�www
		startUrl=removeWwwFromUrl(startUrl);
		toCrawlList.add(startUrl);
		while (toCrawlList.size()>0){//����������һ��srtarturl
			if(maxUrls!=-1){//         ǰ���Ѿ��жϹ��Ǹ�
				if(crawledList.size()==maxUrls){//����������url�ﵽ�û������Ҫ��������url��ʱ��ֹͣ
					break;
				}
			}
			String url=toCrawlList.iterator().next();//��ȡlist����һ��Ԫ�أ�ʵ����Ҳ�����һ��Ԫ��
			toCrawlList.remove(url);//��list���Ƴ�url
			URL verifiedUrl=verifyUrl(url);//ת��String���͵�urlΪURL����
			if(!isRobotAllowed(verifiedUrl)){
				continue;
			}
			
			//�����Ѵ����URL��crawledList
			crawledList.add(url);
			String pageContents=downloadPage(verifiedUrl);//����ҳ���е����ݣ������ַ���
			if(pageContents!=null&&pageContents.length()>0){
				//��ҳ���л�ȡ��Ч����
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
	 * ��URL��ȥ��www
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
	 * ���URL��ʽ
	 * @param url
	 * @return
	 */
	private URL verifyUrl(String url){
		//ֻ����HTTP URLs
		if(!url.toLowerCase().startsWith("http://"))
			return null;
		
		URL verifiedUrl=null;
		try{
			verifiedUrl=new URL(url);//ת��ΪURL����
		}catch(Exception e){
				return null;
		}
		return verifiedUrl;
	}

	/**
	 * ���robot�ǹ�������ʸ�����URL
	 * 
	 */
	private boolean isRobotAllowed(URL urlToCheck){
		String host=urlToCheck.getHost().toLowerCase();//��ȡ����URL������
		/*System.out.println("�����ǣ�"+host);*/
		//��ȡ����������������URL����
		ArrayList<String> disallowList=disallowListCache.get(host);
		//�����û�л��棬���ز�����
		if(disallowList==null){
			disallowList=new ArrayList<String>();
			try{
				URL robotsFileUrl=new URL("http://"+host+"/robots.txt");
				BufferedReader reader=new BufferedReader(new InputStreamReader(robotsFileUrl.openStream()));
				//��ȡrobot�ļ���������������ʵ�·���б�
				String line;
				while((line=reader.readLine())!=null){
					if(line.indexOf("disallow:")==0){//�Ƿ����"Disallow"
						String disallowPath=line.substring("Disallow:".length());//��ȡ���������·������Disallow:������ַ���
						//����Ƿ���ע��
						int commentIndex=disallowPath.indexOf("#");
						if(commentIndex!=-1){//���ҵ���
							disallowPath=disallowPath.substring(0,commentIndex);//ȥ��ע��
						}
						disallowPath=disallowPath.trim();//ȥ���հ�
						disallowList.add(disallowPath);
						
					}
				}
				disallowListCache.put(host, disallowList);//�����������������ʵ�·��
				
			}catch(Exception e){
				return true;//webվ���¸�Ŀ¼��û��rotbots.txt�ļ���������
			}
		}
		
		String file=urlToCheck.getFile();//��getPath�������ƣ�����·����
		/*System.out.println("�ļ�getFile()="+file);*/
		for(int i=0;i<disallowList.size();i++){//�����ÿ��url���жϣ��Ƿ�Ϊ��ֹ���ʵ�url������ǣ�����true
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
			//��URL���ӽ��ж�ȡ
			BufferedReader reader=new BufferedReader(new InputStreamReader(pageUrl.openStream()));
			//��page����buffer
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
	 * j����ҳ�沢�ҳ�����
	 * @param pageUrl
	 * @param pageContents
	 * @param crawledList
	 * @param limitHost
	 * @return
	 */
	 private ArrayList<String> retrieveLinks(URL pageUrl,String pageContents,HashSet crawledList,boolean limitHost){
		 //��������ʽ�������ӵ�ƥ��ģʽ
		 Pattern p=Pattern.compile("<a\\s+href\\s*=\\s*\"?(.*?)[\"|>]",Pattern.CASE_INSENSITIVE);//CASE_INSENSITIVE:ʹ�����ִ�Сд��ƥ�䡣
		 Matcher m=p.matcher(pageContents);
		 
		 ArrayList<String> linkList=new ArrayList<String>();
		 while(m.find()){//find():���Բ������ģʽƥ����������е���һ�������С�
			 String link=m.group(1).trim();//group(int group):��������ǰƥ������ڼ��ɸ����鲶������������С�
			 if(link.length()<1){
				 continue;
			 }
			 //����������ҳ���ڵ�����
			 if(link.charAt(0)=='#'){
				 continue;
			 }
			 
			 if(link.indexOf("mailto:")!=-1){//�ʼ���ַ
				 continue;
			 }
			 if(link.toLowerCase().indexOf("javascript")!=-1){
				 continue;
			 }
			 
			 if(link.indexOf("://")==-1){
				 if(link.charAt(0)=='/'){//������Ե�ַ
					 link="http://"+pageUrl.getHost()+":"+pageUrl.getPort()+link;
				 }else{
					 String file=pageUrl.getFile();
					 if(file.indexOf('/')==-1){//������Ե�ַ
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
			 //����޶��������ų���Щ����������URL
			 if(limitHost&&!pageUrl.getHost().equals(verifiedLink.getHost().toLowerCase())){
				 continue;
			 }
			 
			 //������Щ�Ѿ����������
			 if(crawledList.contains(link)){
				 continue;
			 }
			 linkList.add(link);
			 
		 }
		 return linkList;
	 }
	
	 
	 //��������webҳ������ݣ��ж��ڸ�ҳ������û��ָ���������ַ���
	 private boolean searchStringMatches(String pageContents,String searchString,boolean caseSensitive){
		 String searchContents=pageContents;
		 if(!caseSensitive){//��������ִ�Сд
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
		 SearchCrawler crawler=new SearchCrawler("http://www.sina.com.cn/", 50, "Ů");
		 Thread search=new Thread(crawler);
		 System.out.println("start searching ...");
		 System.out.println("result:");
		 search.start();
	 }

}